package com.cloudrive.fileAsyncService.service.impl;

import com.cloudrive.fileAsyncService.model.entity.FileInfo;
import com.cloudrive.fileAsyncService.service.FileInfoMapperService;
import com.cloudrive.fileAsyncService.service.FolderArchiveService;
import com.cloudrive.fileAsyncService.service.StorageService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件夹 ZIP 打包实现（从单体 FolderServiceImpl 迁入，逻辑保持一致）
 * <p>重名文件通过添加 (数字) 后缀区分，避免 ZIP 包内路径冲突。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FolderArchiveServiceImpl implements FolderArchiveService {

    private final FileInfoMapperService fileInfoService;
    private final StorageService storageService;

    @Override
    public void writeFolderArchive(long fileId, Long ownerUserId, OutputStream output,
                                   LongConsumer progressCallback, BooleanSupplier cancelled) {
        FileInfo root = requireFolder(fileId, ownerUserId);
        writeArchive(root, ownerUserId, output,
                progressCallback == null ? ignored -> {
                } : progressCallback,
                cancelled == null ? () -> false : cancelled);
    }

    private void writeArchive(FileInfo root, Long ownerUserId, OutputStream output,
                              LongConsumer progressCallback, BooleanSupplier cancelled) {
        String rootDirectory = normalizeDirectory(root.getLocalFilePath());
        List<FileInfo> subtree = fileInfoService.getSubTree(rootDirectory, ownerUserId);
        subtree.sort(Comparator.comparing(info -> normalizePath(info.getLocalFilePath())));
        Set<String> usedEntries = new HashSet<>();
        String zipRoot = safeSegment(root.getFilename()) + "/";
        byte[] buffer = new byte[64 * 1024];
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.setLevel(5);
            addDirectoryEntry(zip, zipRoot, usedEntries);
            for (FileInfo info : subtree) {
                if (cancelled.getAsBoolean()) throw new CancellationException("文件夹下载任务已取消");
                if (info.getId() != null && info.getId().equals(root.getId())) continue;
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
                        if (cancelled.getAsBoolean()) throw new CancellationException("文件夹下载任务已取消");
                        if (read == 0) continue;
                        zip.write(buffer, 0, read);
                        progressCallback.accept(read);
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

    private FileInfo requireFolder(long fileId, Long ownerUserId) {
        FileInfo root = fileInfoService.findByIdAndUserId(fileId, ownerUserId);
        if (root == null || Boolean.TRUE.equals(root.getIsDeleted())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(root.getIsFolder())) {
            throw new BusinessException(ErrorCode.CANNOT_DOWNLOAD_FOLDER, "目标不是文件夹");
        }
        return root;
    }

    private void addDirectoryEntry(ZipOutputStream zip, String path, Set<String> used) throws IOException {
        String directory = path.endsWith("/") ? path : path + "/";
        if (used.add(directory)) {
            zip.putNextEntry(new ZipEntry(directory));
            zip.closeEntry();
        }
    }

    private String toRelativePath(String rootDirectory, FileInfo info) {
        String fullPath = normalizePath(info.getLocalFilePath());
        String relative = fullPath.startsWith(rootDirectory) ? fullPath.substring(rootDirectory.length()) : "";
        if (relative.isBlank()) {
            relative = displayName(info);
        }
        relative = relative.replace("../", "").replace("..\\", "");
        if (Boolean.TRUE.equals(info.getIsFolder()) && !relative.endsWith("/")) relative += "/";
        return relative;
    }

    private String uniqueEntryName(String requested, boolean directory, Set<String> used) {
        String normalized = requested.replace('\\', '/');
        if (directory && !normalized.endsWith("/")) normalized += "/";
        if (!used.contains(normalized)) {
            if (!directory) used.add(normalized);
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
        if (!directory) used.add(candidate);
        return candidate;
    }

    private String displayName(FileInfo info) {
        String name = info.getOriginalFilename();
        if (name == null || name.isBlank()) name = info.getFilename();
        if (name == null || name.isBlank()) name = "unnamed-" + info.getId();
        return safeSegment(name);
    }

    private String safeSegment(String value) {
        if (value == null || value.isBlank()) return "folder";
        return value.replace('/', '_').replace('\\', '_').replace("..", "_");
    }

    private String normalizeDirectory(String path) {
        String normalized = normalizePath(path);
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }
}
