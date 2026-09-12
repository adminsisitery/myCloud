package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.FileTypeUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FolderService;
import com.ff.mycloud.service.StorageService;
import com.ff.mycloud.service.StorageServiceFactory;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
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
 * 文件夹服务实现类
 * <p>提供文件夹的创建（含合法性校验）和下载打包（ZIP）功能。
 * 下载时自动处理重名文件，避免ZIP包内路径冲突。</p>
 *
 * @author 飞飞
 * @date 2026/3/11
 * @version 1.0
 */

@Slf4j
@Service
public class FolderServiceImpl implements FolderService {

    FileInfoMapperService fileInfoMapperService;
    FileMapper fileMapper;
    StorageServiceFactory storageServiceFactory;

    public FolderServiceImpl(FileInfoMapperService fileInfoMapperService,
                             FileMapper fileMapper,
                             StorageServiceFactory storageServiceFactory) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileMapper = fileMapper;
        this.storageServiceFactory = storageServiceFactory;
    }

    /**
     * 创建文件夹
     * <p>校验流程：名称去空格 -> 空值检查 -> 长度限制(30字符) -> 非法字符检查 -> 重名检查 -> 计算本地路径 -> 保存记录。</p>
     *
     * @param parentId   父目录ID，为null时在根目录创建
     * @param folderName 文件夹名称
     * @return 操作结果
     */
    @Override
    public Result<String> createFolder(Long parentId, String folderName) {

        // 查看是否为null
        if (folderName == null || folderName.trim().isEmpty()) {
            return Result.error(500, "文件夹名不能为空");
        }
        folderName = folderName.trim();
        //获得当前操作用户
        Long currentUserId = UserContext.getCurrentUserId();
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
        String localFilePath;
        if (parentId == null || parentId == 0) {
            localFilePath = folderName + "/";
        } else {
            FileInfo parent = fileInfoMapperService.findByIdAndUserId(parentId, currentUserId);
            if (parent == null || !Boolean.TRUE.equals(parent.getIsFolder())) {
                return Result.error(404, "父文件夹不存在或无权访问");
            }
            localFilePath = normalizeDirectory(parent.getLocalFilePath()) + folderName + "/";
        }

        //创建文件夹
        FileInfo fileInfoFromFolder = fileMapper.toFileInfoFromFolder(folderName, true, false, parentId, currentUserId,localFilePath);
        fileInfoFromFolder.setCategoryId(FileTypeUtil.getCategoryId(null)); // 文件夹归为"其他"
        fileInfoMapperService.save(fileInfoFromFolder);
//        //在ES里创建文件夹
//        fileSearchService.save(fileInfoFromFolder.getId(),fileInfoFromFolder.getFilename(),UserContext.getCurrentUserId());
        return Result.success("创建成功");
    }

    /**
     * 下载整个文件夹（打包为ZIP）
     * <p>流程：校验文件 -> 路径标准化 -> 获取子树 -> 重名文件处理 -> 逐个写入ZIP -> 输出响应流。
     * 重名文件通过添加(数字)后缀进行区分。</p>
     *
     * @param fileId   文件夹ID
     * @param response HTTP响应对象，用于输出ZIP流
     * @throws RuntimeException 当IO异常时抛出
     */
    @Override
    public void downLoadFolder(long fileId, HttpServletResponse response) {
        writeFolderZip(fileId, UserContext.getCurrentUserId(), response);
    }

    @Override
    public void downLoadSharedFolder(long fileId, Long ownerUserId, HttpServletResponse response) {
        if (ownerUserId == null) throw new BusinessException(ErrorCode.NO_PERMISSION);
        writeFolderZip(fileId, ownerUserId, response);
    }

    private void writeFolderZip(long fileId, Long ownerUserId, HttpServletResponse response) {
        FileInfo root = requireFolder(fileId, ownerUserId);
        prepareZipResponse(response, root.getFilename());
        try (OutputStream output = response.getOutputStream()) {
            writeArchive(root, ownerUserId, output, ignored -> {}, () -> false);
        } catch (IOException e) {
            log.error("文件夹下载失败: fileId={}, ownerUserId={}", fileId, ownerUserId, e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED, "文件夹打包下载失败");
        }
    }

    @Override
    public void writeFolderArchive(long fileId, Long ownerUserId, OutputStream output,
                                   LongConsumer progressCallback, BooleanSupplier cancelled) {
        FileInfo root = requireFolder(fileId, ownerUserId);
        writeArchive(root, ownerUserId, output,
                progressCallback == null ? ignored -> {} : progressCallback,
                cancelled == null ? () -> false : cancelled);
    }

    private void writeArchive(FileInfo root, Long ownerUserId, OutputStream output,
                              LongConsumer progressCallback, BooleanSupplier cancelled) {
        String rootDirectory = normalizeDirectory(root.getLocalFilePath());
        List<FileInfo> subtree = fileInfoMapperService.getSubTree(rootDirectory, ownerUserId);
        subtree.sort(Comparator.comparing(info -> normalizePath(info.getLocalFilePath())));
        StorageService storage = storageServiceFactory.getStorageService();
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
                try (InputStream input = storage.getFile(info.getPath())) {
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
        FileInfo root = fileInfoMapperService.findByIdAndUserId(fileId, ownerUserId);
        if (root == null || Boolean.TRUE.equals(root.getIsDeleted())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(root.getIsFolder())) {
            throw new BusinessException(ErrorCode.CANNOT_DOWNLOAD_FOLDER, "目标不是文件夹");
        }
        return root;
    }

    private void prepareZipResponse(HttpServletResponse response, String folderName) {
        String filename = folderName == null || folderName.isBlank() ? "folder" : folderName;
        if (filename.toLowerCase().endsWith(".zip")) filename = filename.substring(0, filename.length() - 4);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", ContentDisposition.attachment()
                .filename(filename + ".zip", StandardCharsets.UTF_8).build().toString());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
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
