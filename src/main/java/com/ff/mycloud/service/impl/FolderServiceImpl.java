package com.ff.mycloud.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FolderService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/11 15:50
 * @version 1.0
 */

@Slf4j
@Service
public class FolderServiceImpl implements FolderService {

    FileInfoMapperService fileInfoMapperService;
    FileMapper fileMapper;
    OssStorageServiceImpl ossStorageServiceImpl;

    public FolderServiceImpl(FileInfoMapperService fileInfoMapperService, FileMapper fileMapper, OssStorageServiceImpl ossStorageServiceImpl) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileMapper = fileMapper;
        this.ossStorageServiceImpl = ossStorageServiceImpl;
    }

    @Override
    public Result<String> createFolder(Long parentId, String folderName) {

        folderName=folderName.trim();
        //获得当前操作用户
        Long currentUserId = UserContext.getCurrentUserId();
        //查看是否为null
        if (folderName==null||folderName.trim().isEmpty()) {
            return Result.error(500,"文件夹名不能为空");
        }
        //限制文件夹名长度
        if(folderName.length() > 30){
            return Result.error(500,"名称过长");
        }
        //限制出现字符
        String regex = "[\\\\/:*?\"<>|]";

        if(folderName.matches(".*"+regex+".*")){
            return Result.error(500,"文件名包含非法字符");
        }
        //判断是否有重名文件夹
        if (fileInfoMapperService.existsFolder(parentId,folderName,currentUserId)) {
            return Result.error(500,"同级文件夹重名");
        }
        String localFilePath="";
        if (parentId!=null){
            //有父类目录的情况下获得父目录的localpath
            FileInfo byId = fileInfoMapperService.getById(parentId);
            if (byId!=null){
                //如果为不空那就是在上层文件夹
                localFilePath= byId.getLocalFilePath();
            }
            localFilePath = localFilePath+folderName+"/";
        }

        //创建文件夹
        FileInfo fileInfoFromFolder = fileMapper.toFileInfoFromFolder(folderName, true, false, parentId, currentUserId,localFilePath);
        fileInfoMapperService.save(fileInfoFromFolder);
        return Result.success("创建成功");
    }

    @Override
    public void downLoadFolder(long fileId, HttpServletResponse response) {
        //检验fileId 用户 文件夹 是否删除
        FileInfo fileInfo = fileInfoMapperService.getById(fileId);
        if (fileInfo==null||fileInfo.getIsDeleted()){
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!fileInfo.getIsFolder()){
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        Long currentUserId = UserContext.getCurrentUserId();
        if (!currentUserId.equals(fileInfo.getUserId())){
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        String localFilePath = fileInfo.getLocalFilePath();
        //路径标准化
        if(localFilePath.endsWith("/")){
            localFilePath = localFilePath.substring(0,localFilePath.length()-1);
        }
        List<FileInfo> subTree = fileInfoMapperService.getSubTree(localFilePath, currentUserId);
        log.info("飞飞飞："+localFilePath+"|*|"+currentUserId+"|||"+subTree);
        //下载打包到ZIP
            ZipOutputStream zop = null;

        // ========== 极简重命名逻辑（适配你的get方法） ==========
        Map<String, FileInfo> uniqueFileMap = new HashMap<>();
        for (FileInfo info : subTree) {
            if (info.getIsFolder()) {continue;} // 跳过文件夹

            String originalPath = info.getLocalFilePath();
            String finalPath = originalPath;
            int count = 1;

            // 重复则加 (数字) 后缀
            while (uniqueFileMap.containsKey(finalPath)) {
                // 直接用你的get方法获取文件名和后缀
                String pureName = info.getFilename(); // 新建 文本文档
                String suffix = info.getFileSuffix(); // txt

                // 拆分目录（只拆一次，复用你的完整路径）
                int lastSlash = originalPath.lastIndexOf("/");
                String dir = lastSlash > 0 ? originalPath.substring(0, lastSlash + 1) : "";

                // 拼接新路径：目录 + 纯文件名(数字) + .后缀（无后缀则不加）
                if (suffix.isEmpty()) {
                    finalPath = dir + pureName + "(" + count + ")";
                } else {
                    finalPath = dir + pureName + "(" + count + ")." + suffix;
                }
                count++;
                log.warn("重复文件重命名：{} → {}", originalPath, finalPath);
            }
            uniqueFileMap.put(finalPath, info);
        }


        try {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String filename = fileInfo.getFilename();
            if (!filename.endsWith(".zip")){
                int lastDotIndex = filename.lastIndexOf(".");
                // 只有当找到点，且点不在第一位时才截取
                if (lastDotIndex > 0) {
                    filename = filename.substring(0, lastDotIndex);
                }
            }
            // 处理中文文件名乱码（兼容不同浏览器）
            String fileName = URLEncoder.encode(filename + ".zip", StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            // 禁用缓存、设置ZIP编码
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Content-Encoding", "zip");


            OutputStream outputStream = response.getOutputStream();
            zop = new ZipOutputStream(outputStream);
            zop.setLevel(5);
        for (Map.Entry<String, FileInfo> entry : uniqueFileMap.entrySet()) {
            String zipPath = entry.getKey(); // 用重命名后的路径（核心修复！）
            FileInfo info = entry.getValue();
            if (info.getIsFolder()){
                continue;
            }

            ZipEntry zipEntry = new ZipEntry(zipPath);
            zop.putNextEntry(zipEntry);

            String path = info.getPath();
            byte[] files = ossStorageServiceImpl.downloadFile(path);
            if (files!=null&&files.length>0){
                zop.write(files);
                zop.flush();
            }
            zop.closeEntry();
        }
        zop.finish();
        outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
