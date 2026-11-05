package com.cloudrive.fileManageService.service.impl;

import com.cloudrive.fileManageService.mapper.FileMapper;
import com.cloudrive.fileManageService.model.entity.FileInfo;
import com.cloudrive.fileManageService.mq.MqProducer;
import com.cloudrive.fileManageService.repositorys.FileObjectMapper;
import com.cloudrive.fileManageService.repositorys.FileRecentAccess;
import com.cloudrive.fileManageService.service.FileInfoMapperService;
import com.cloudrive.pan_common.result.Result;
import com.cloudrive.pan_common.util.UserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * renameFile 单元测试：对照源码每个 if/return 分支写一条。
 * 依赖（Mapper/MQ）全部 @Mock 掉，只测 Service 里的判断逻辑。
 */
@ExtendWith(MockitoExtension.class)
class FileManageServiceImplTest {

    @Mock FileInfoMapperService fileInfoMapperService;
    @Mock FileMapper fileMapper;
    @Mock FileObjectMapper fileObjectMapper;
    @Mock FileRecentAccess fileRecentAccess;
    @Mock MqProducer mqProducer;

    @InjectMocks FileManageServiceImpl service;

    /** 造一个「本人、未删除」的文件，供走到权限校验之后的用例复用。 */
    private FileInfo ownedFile(Long id, Long userId) {
        FileInfo file = new FileInfo();
        file.setId(id);
        file.setUserId(userId);
        file.setIsDeleted(false);
        return file;
    }

    // ---- 分支 1：newFilename 为空 / 空白 ----
    @Test
    void renameFile_新文件名为空时返回错误() {
        assertThat(service.renameFile(1L, null).getMessage()).isEqualTo("新文件名不能为空");
        assertThat(service.renameFile(1L, "   ").getMessage()).isEqualTo("新文件名不能为空");
    }

    // ---- 分支 2：文件不存在 ----
    @Test
    void renameFile_文件不存在时返回错误() {
        when(fileInfoMapperService.getById(99L)).thenReturn(null);

        Result<String> r = service.renameFile(99L, "新名字");

        assertThat(r.getMessage()).isEqualTo("文件不存在");
    }

    // ---- 分支 3a：他人文件 ----
    @Test
    void renameFile_他人文件时返回无权操作() {
        FileInfo file = ownedFile(1L, 2L);   // 文件属于用户 2
        when(fileInfoMapperService.getById(1L)).thenReturn(file);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(1L);  // 当前登录是用户 1

            Result<String> r = service.renameFile(1L, "新名字");

            assertThat(r.getMessage()).isEqualTo("无权操作该文件");
        }
    }

    // ---- 分支 3b：文件已在回收站 ----
    @Test
    void renameFile_已删除文件时返回无权操作() {
        FileInfo file = ownedFile(1L, 1L);
        file.setIsDeleted(true);
        when(fileInfoMapperService.getById(1L)).thenReturn(file);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(1L);

            Result<String> r = service.renameFile(1L, "新名字");

            assertThat(r.getMessage()).isEqualTo("无权操作该文件");
        }
    }

    // ---- 分支 4：文件名超长（> 50）----
    @Test
    void renameFile_文件名超长时返回不合法() {
        FileInfo file = ownedFile(1L, 1L);
        file.setIsFolder(false);
        when(fileInfoMapperService.getById(1L)).thenReturn(file);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(1L);

            Result<String> r = service.renameFile(1L, "a".repeat(51));

            assertThat(r.getMessage()).isEqualTo("文件名不合法");
        }
    }

    // ---- 分支 5：同级目录已存在同名文件 ----
    @Test
    void renameFile_同级已存在同名文件时返回错误() {
        FileInfo file = ownedFile(1L, 1L);
        file.setIsFolder(false);
        file.setFilename("旧名字");
        file.setFileSuffix("txt");
        file.setParentId(0L);
        when(fileInfoMapperService.getById(1L)).thenReturn(file);

        FileInfo sibling = new FileInfo();
        sibling.setId(2L);
        sibling.setFilename("新名字");
        sibling.setFileSuffix("txt");
        sibling.setIsFolder(false);
        when(fileInfoMapperService.findByParentIdAndUserId(1L, 0L))
                .thenReturn(List.of(file, sibling));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(1L);

            Result<String> r = service.renameFile(1L, "新名字.txt");

            assertThat(r.getMessage()).isEqualTo("目标目录已存在同名文件");
        }
    }

    // ---- 分支 6：成功路径（普通文件）----
    @Test
    void renameFile_文件重命名成功() {
        FileInfo file = ownedFile(1L, 1L);
        file.setIsFolder(false);
        file.setFilename("旧名字");
        file.setFileSuffix("txt");
        file.setParentId(0L);
        file.setLocalFilePath("/旧名字.txt");
        when(fileInfoMapperService.getById(1L)).thenReturn(file);
        when(fileInfoMapperService.findByParentIdAndUserId(1L, 0L)).thenReturn(List.of(file));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(1L);

            Result<String> r = service.renameFile(1L, "新名字.txt");

            // 成功时 message 固定是 "success"，真正的内容在 data 里
            assertThat(r.getMessage()).isEqualTo("success");
            assertThat(r.getData()).isEqualTo("重命名成功");
            assertThat(file.getFilename()).isEqualTo("新名字");   // 文件名已更新
            assertThat(file.getLocalFilePath()).isEqualTo("/新名字.txt");
            verify(fileInfoMapperService).updateById(file);        // 确认落库被调用
        }
    }

    // ---- 分支 7：成功路径（文件夹，会级联更新子树路径）----
    @Test
    void renameFile_文件夹重命名时更新子树路径() {
        FileInfo folder = ownedFile(1L, 1L);
        folder.setIsFolder(true);
        folder.setFilename("旧文件夹");
        folder.setParentId(0L);
        folder.setLocalFilePath("/旧文件夹/");
        when(fileInfoMapperService.getById(1L)).thenReturn(folder);
        when(fileInfoMapperService.findByParentIdAndUserId(1L, 0L)).thenReturn(List.of(folder));

        FileInfo child = new FileInfo();
        child.setId(2L);
        child.setLocalFilePath("/旧文件夹/子文件.txt");
        when(fileInfoMapperService.getSubTree("/旧文件夹/", 1L)).thenReturn(List.of(child));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(1L);

            service.renameFile(1L, "新文件夹");

            assertThat(child.getLocalFilePath()).isEqualTo("/新文件夹/子文件.txt");
            verify(fileInfoMapperService).updateById(child);
        }
    }
}
