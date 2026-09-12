package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.StorageService;
import com.ff.mycloud.service.StorageServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FolderServiceImplTest {
    @Mock private FileInfoMapperService fileInfoService;
    @Mock private FileMapper fileMapper;
    @Mock private StorageServiceFactory storageFactory;
    @Mock private StorageService storage;

    private FolderServiceImpl service;
    private MockedStatic<UserContext> userContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FolderServiceImpl(fileInfoService, fileMapper, storageFactory);
        when(storageFactory.getStorageService()).thenReturn(storage);
        userContext = mockStatic(UserContext.class);
        userContext.when(UserContext::getCurrentUserId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        userContext.close();
    }

    @Test
    void streamsFilesAndKeepsEmptyDirectories() throws Exception {
        FileInfo root = folder(10L, 1L, "资料", "资料/");
        FileInfo empty = folder(11L, 1L, "空目录", "资料/空目录/");
        FileInfo first = file(12L, 1L, "a.txt", "资料/a.txt", "oss/a");
        FileInfo nested = file(13L, 1L, "b.txt", "资料/子目录/b.txt", "oss/b");
        when(fileInfoService.findByIdAndUserId(10L, 1L)).thenReturn(root);
        when(fileInfoService.getSubTree("资料/", 1L)).thenReturn(new ArrayList<>(List.of(root, empty, first, nested)));
        when(storage.getFile("oss/a")).thenReturn(new ByteArrayInputStream("alpha".getBytes(StandardCharsets.UTF_8)));
        when(storage.getFile("oss/b")).thenReturn(new ByteArrayInputStream("beta".getBytes(StandardCharsets.UTF_8)));

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.downLoadFolder(10L, response);

        assertEquals("application/zip", response.getContentType());
        assertNull(response.getHeader("Content-Encoding"));
        List<ZipItem> items = readZip(response.getContentAsByteArray());
        assertTrue(items.stream().anyMatch(i -> i.name.equals("资料/空目录/")));
        assertEquals("alpha", content(items, "资料/a.txt"));
        assertEquals("beta", content(items, "资料/子目录/b.txt"));
        verify(storage, never()).downloadFile(anyString());
    }

    @Test
    void sharedDownloadUsesOwnerInsteadOfCurrentUser() {
        FileInfo root = folder(20L, 99L, "共享", "共享/");
        when(fileInfoService.findByIdAndUserId(20L, 99L)).thenReturn(root);
        when(fileInfoService.getSubTree("共享/", 99L)).thenReturn(new ArrayList<>(List.of(root)));

        service.downLoadSharedFolder(20L, 99L, new MockHttpServletResponse());

        verify(fileInfoService).findByIdAndUserId(20L, 99L);
        verify(fileInfoService, never()).findByIdAndUserId(20L, 1L);
    }

    @Test
    void rejectsMissingOrUnauthorizedFolderBeforeWritingResponse() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(BusinessException.class, () -> service.downLoadFolder(404L, response));
        verify(storageFactory, never()).getStorageService();
        assertEquals(0, response.getContentAsByteArray().length);
    }

    @Test
    void rootFolderAlwaysGetsAUsableLocalPath() {
        FileInfo mapped = new FileInfo();
        when(fileMapper.toFileInfoFromFolder(eq("新目录"), eq(true), eq(false), isNull(), eq(1L), eq("新目录/")))
                .thenReturn(mapped);

        service.createFolder(null, "新目录");

        verify(fileInfoService).save(mapped);
    }

    private FileInfo folder(Long id, Long userId, String name, String localPath) {
        FileInfo file = base(id, userId, name, localPath);
        file.setIsFolder(true);
        file.setPath("/");
        return file;
    }

    private FileInfo file(Long id, Long userId, String name, String localPath, String storagePath) {
        FileInfo file = base(id, userId, name, localPath);
        file.setIsFolder(false);
        file.setOriginalFilename(name);
        file.setPath(storagePath);
        return file;
    }

    private FileInfo base(Long id, Long userId, String name, String localPath) {
        FileInfo file = new FileInfo();
        file.setId(id);
        file.setUserId(userId);
        file.setFilename(name);
        file.setLocalFilePath(localPath);
        file.setIsDeleted(false);
        return file;
    }

    private List<ZipItem> readZip(byte[] bytes) throws Exception {
        List<ZipItem> result = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                zip.transferTo(content);
                result.add(new ZipItem(entry.getName(), content.toString(StandardCharsets.UTF_8)));
            }
        }
        return result;
    }

    private String content(List<ZipItem> items, String name) {
        return items.stream().filter(i -> i.name.equals(name)).findFirst().orElseThrow().content;
    }

    private record ZipItem(String name, String content) {}
}
