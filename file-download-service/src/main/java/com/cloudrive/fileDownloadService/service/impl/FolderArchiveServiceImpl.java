package com.cloudrive.fileDownloadService.service.impl;

import com.cloudrive.fileDownloadService.model.entity.FileInfo;
import com.cloudrive.fileDownloadService.service.FileInfoMapperService;
import com.cloudrive.fileDownloadService.service.FolderArchiveService;
import com.cloudrive.fileDownloadService.service.StorageService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.UserContext;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件夹同步打包下载实现
 * <p>
 * ZIP 写入逻辑从原单体 {@code FolderServiceImpl#downLoadFolder/writeArchive}
 * 迁移而来（与分享服务的文件夹打包逻辑一致）：按 local_file_path 前缀取整棵
 * 子树，流式写入 ZIP，重名自动追加 (n)。所属人来自当前登录用户。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class FolderArchiveServiceImpl implements FolderArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(FolderArchiveServiceImpl.class);

    /** ZIP 复制缓冲区大小 */
    private static final int BUFFER_SIZE = 64 * 1024;

    private final FileInfoMapperService fileInfoMapperService;
    private final StorageService storageService;

    public FolderArchiveServiceImpl(FileInfoMapperService fileInfoMapperService,
                                    StorageService storageService) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.storageService = storageService;
    }

    @Override
    public void downLoadFolder(long fileId, HttpServletResponse response) {
        Long currentUserId = UserContext.getCurrentUserId();
        FileInfo root = requireFolder(fileId, currentUserId);
        prepareZipResponse(response, root.getFilename());
        try (OutputStream output = response.getOutputStream()) {
            writeArchive(root, currentUserId, output);
        } catch (IOException e) {
            logger.error("文件夹打包下载失败: fileId={}, userId={}", fileId, currentUserId, e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED, "文件夹打包下载失败");
        }
    }

    /**
     * 递归写 ZIP：目录条目 + 每个文件的对象存储流
     *
     * @param root        根文件夹
     * @param ownerUserId 所属用户 ID
     * @param output      输出流
     */
    private void writeArchive(FileInfo root, Long ownerUserId, OutputStream output) {
        String rootDirectory = normalizeDirectory(root.getLocalFilePath());
        List<FileInfo> subtree = fileInfoMapperService.getSubTree(rootDirectory, ownerUserId);
        subtree.sort(Comparator.comparing(info -> normalizePath(info.getLocalFilePath())));
        Set<String> usedEntries = new HashSet<>();
        String zipRoot = safeSegment(root.getFilename()) + "/";
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.setLevel(5);
            addDirectoryEntry(zip, zipRoot, usedEntries);
            for (FileInfo info : subtree) {
                if (info.getId() != null && info.getId().equals(root.getId())) {
                    continue;
                }
                String relativePath = toRelativePath(rootDirectory, info);
                String entryName = uniqueEntryName(zipRoot + relativePath,
                        Boolean.TRUE.equals(info.getIsFolder()), usedEntries);
                if (Boolean.TRUE.equals(info.getIsFolder())) {
                    addDirectoryEntry(zip, entryName, usedEntries);
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream input = storageService.getFile(info.getPath())) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            zip.write(buffer, 0, read);
                        }
                    }
                } finally {
                    zip.closeEntry();
                }
            }
            zip.finish();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED, "文件夹打包失败: " + e.getMessage());
        }
    }

    /**
     * 校验并返回根文件夹
     *
     * @param fileId 文件夹 ID
     * @param userId 所属用户 ID
     * @return 文件夹实体
     */
    private FileInfo requireFolder(long fileId, Long userId) {
        FileInfo root = fileInfoMapperService.findByIdAndUserId(fileId, userId);
        if (root == null || Boolean.TRUE.equals(root.getIsDeleted())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(root.getIsFolder())) {
            throw new BusinessException(ErrorCode.CANNOT_DOWNLOAD_FOLDER, "目标不是文件夹");
        }
        return root;
    }

    /**
     * 设置 ZIP 下载响应头
     *
     * @param response   HTTP 响应
     * @param folderName 文件夹名
     */
    private void prepareZipResponse(HttpServletResponse response, String folderName) {
        String filename = folderName == null || folderName.isBlank() ? "folder" : folderName;
        if (filename.toLowerCase().endsWith(".zip")) {
            filename = filename.substring(0, filename.length() - 4);
        }
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", ContentDisposition.attachment()
                .filename(filename + ".zip", StandardCharsets.UTF_8).build().toString());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    /**
     * 写入目录条目（去重）
     *
     * @param zip  ZIP 输出流
     * @param path 目录路径
     * @param used 已使用条目集合
     * @throws IOException 写入异常
     */
    private void addDirectoryEntry(ZipOutputStream zip, String path, Set<String> used) throws IOException {
        String directory = path.endsWith("/") ? path : path + "/";
        if (used.add(directory)) {
            zip.putNextEntry(new ZipEntry(directory));
            zip.closeEntry();
        }
    }

    /**
     * 计算文件在 ZIP 内的相对路径
     *
     * @param rootDirectory 根目录路径
     * @param info          文件信息
     * @return 相对路径
     */
    private String toRelativePath(String rootDirectory, FileInfo info) {
        String fullPath = normalizePath(info.getLocalFilePath());
        String relative = fullPath.startsWith(rootDirectory) ? fullPath.substring(rootDirectory.length()) : "";
        if (relative.isBlank()) {
            relative = displayName(info);
        }
        relative = relative.replace("../", "").replace("..\\", "");
        if (Boolean.TRUE.equals(info.getIsFolder()) && !relative.endsWith("/")) {
            relative += "/";
        }
        return relative;
    }

    /**
     * 生成唯一 ZIP 条目名（重名追加 (n)）
     *
     * @param requested 期望条目名
     * @param directory 是否为目录
     * @param used      已使用条目集合
     * @return 唯一条目名
     */
    private String uniqueEntryName(String requested, boolean directory, Set<String> used) {
        String normalized = requested.replace('\\', '/');
        if (directory && !normalized.endsWith("/")) {
            normalized += "/";
        }
        if (!used.contains(normalized)) {
            if (!directory) {
                used.add(normalized);
            }
            return normalized;
        }
        String noSlash = directory ? normalized.substring(0, normalized.length() - 1) : normalized;
        int slash = noSlash.lastIndexOf('/');
        String dir = slash >= 0 ? noSlash.substring(0, slash + 1) : "";
        String name = slash >= 0 ? noSlash.substring(slash + 1) : noSlash;
        int dot = directory ? -1 : name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        int index = 1;
        String candidate;
        do {
            candidate = dir + base + "(" + index++ + ")" + suffix + (directory ? "/" : "");
        } while (used.contains(candidate));
        if (!directory) {
            used.add(candidate);
        }
        return candidate;
    }

    /**
     * 取条目的展示名
     *
     * @param info 文件信息
     * @return 安全展示名
     */
    private String displayName(FileInfo info) {
        String name = info.getOriginalFilename();
        if (name == null || name.isBlank()) {
            name = info.getFilename();
        }
        if (name == null || name.isBlank()) {
            name = "unnamed-" + info.getId();
        }
        return safeSegment(name);
    }

    /**
     * 文件名安全化：替换路径分隔符与 ..
     *
     * @param value 原始名称
     * @return 安全名称
     */
    private String safeSegment(String value) {
        if (value == null || value.isBlank()) {
            return "folder";
        }
        return value.replace('/', '_').replace('\\', '_').replace("..", "_");
    }

    /**
     * 目录路径规范化：统一以 "/" 结尾
     *
     * @param path 原始路径
     * @return 规范化路径
     */
    private String normalizeDirectory(String path) {
        String normalized = normalizePath(path);
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    /**
     * 路径规范化：统一斜杠、去掉开头 "/"
     *
     * @param path 原始路径
     * @return 规范化路径
     */
    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
