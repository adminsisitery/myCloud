package com.cloudrive.fileService.service.impl;

import com.cloudrive.fileService.mapper.FileMapper;
import com.cloudrive.fileService.model.dto.FastUploadRequest;
import com.cloudrive.fileService.model.entity.FileCheck;
import com.cloudrive.fileService.model.entity.FileInfo;
import com.cloudrive.fileService.model.vo.FastUploadVO;
import com.cloudrive.fileService.model.vo.FileCheckVO;
import com.cloudrive.fileService.service.FileFastUploadService;
import com.cloudrive.fileService.service.FileInfoMapperService;
import com.cloudrive.fileService.service.UpLoadProgressService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.result.Result;
import com.cloudrive.pan_common.util.FileTypeUtil;
import com.cloudrive.pan_common.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 文件秒传服务实现
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.service.impl.FileServiceImpl#checkFilesHash} 与
 * {@code #fastUpload}（含私有方法 {@code handleFastUpload}），行为保持一致：
 * </p>
 * <ol>
 *   <li>判定不落库，且要求 hash + size 同时命中，避免假秒传；</li>
 *   <li>执行时重新校验物理对象、校验目标目录归属、保证幂等；</li>
 *   <li>只插入 t_file_info 记录并复用已有存储路径，不动 refCount / OSS 对象。</li>
 * </ol>
 * <p>
 * 说明：与 {@link FileUploadAsyncServiceImpl} 上传链路里的秒传分支同源，
 * 两处都调用 MapStruct 的 {@code toFileInfoForFastUpload} 落库，口径保持一致。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Slf4j
@Service
public class FileFastUploadServiceImpl implements FileFastUploadService {

    /** 合法的 SHA-256 十六进制串 */
    private static final String SHA256_PATTERN = "[0-9a-f]{64}";

    private final FileInfoMapperService fileInfoMapperService;
    private final FileMapper fileMapper;
    private final UpLoadProgressService upLoadProgressService;

    public FileFastUploadServiceImpl(FileInfoMapperService fileInfoMapperService,
                                     FileMapper fileMapper,
                                     UpLoadProgressService upLoadProgressService) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileMapper = fileMapper;
        this.upLoadProgressService = upLoadProgressService;
    }

    @Override
    public Result<FileCheckVO> checkFilesHash(FileCheck fileCheck) {
        String sha256Hash = fileCheck.getSha256Hash();
        Long fileSize = fileCheck.getFileSize();
        // 参数不完整时一律判为不可秒传：没有 size 就无法排除 hash 碰撞/伪造
        if (sha256Hash == null || sha256Hash.isBlank() || fileSize == null) {
            return Result.success(FileCheckVO.builder().exists(false).build());
        }

        List<FileInfo> byHashAndUserId =
                fileInfoMapperService.findByHashAndUserId(sha256Hash, UserContext.getCurrentUserId());
        // 命中还需比对 size，避免假秒传（hash 碰撞或前端篡改 fileSize）
        boolean exists = byHashAndUserId.stream().anyMatch(f -> fileSize.equals(f.getFileSize()));

        // 只做判定，不落库：真正的落库在 fastUpload() 里重新校验后执行
        return Result.success(FileCheckVO.builder().exists(exists).build());
    }

    @Override
    public Result<FastUploadVO> fastUpload(FastUploadRequest request) {
        Long currentUserId = UserContext.getCurrentUserId();

        // ---- 1. 入参校验：全部重算，不信任客户端 ----
        String rawHash = request.getSha256Hash();
        String sha256Hash = rawHash == null ? null : rawHash.trim().toLowerCase();
        if (sha256Hash == null || !sha256Hash.matches(SHA256_PATTERN)) {
            return Result.error(ErrorCode.INVALID_PARAM.getHttpStatus(), "sha256Hash 必须是 64 位十六进制字符串");
        }
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            return Result.error(ErrorCode.INVALID_PARAM.getHttpStatus(), "fileName 不能为空");
        }
        Long fileSize = request.getFileSize();
        if (fileSize == null || fileSize <= 0) {
            return Result.error(ErrorCode.INVALID_PARAM.getHttpStatus(), "fileSize 必须大于 0");
        }

        // ---- 2. 重新确认服务端确有可复用的物理对象 ----
        FileInfo existingFile = fileInfoMapperService.findByHashAndUserId(sha256Hash, currentUserId).stream()
                .filter(f -> fileSize.equals(f.getFileSize()))
                .filter(f -> !Boolean.TRUE.equals(f.getIsFolder()))
                .findFirst()
                .orElse(null);
        if (existingFile == null) {
            return Result.error(ErrorCode.FAST_UPLOAD_UNAVAILABLE.getHttpStatus(),
                    ErrorCode.FAST_UPLOAD_UNAVAILABLE.getMessage());
        }

        // ---- 3. 目标目录归属校验：必须指向当前用户在用的文件夹 ----
        // 根目录统一归一化为 0，避免 parent_id 为 null 时同级查询/落库口径不一致
        Long parentId = request.getParentId() == null ? 0L : request.getParentId();
        if (parentId != 0L) {
            FileInfo parent = fileInfoMapperService.findByIdAndUserId(parentId, currentUserId);
            if (parent == null || Boolean.TRUE.equals(parent.getIsDeleted())
                    || !Boolean.TRUE.equals(parent.getIsFolder())) {
                return Result.error(ErrorCode.FILE_NOT_FOUND.getHttpStatus(), "目标目录不存在");
            }
        }

        // ---- 4. 文件名拆解（与落库逻辑保持一致的口径） ----
        String fileName = request.getFileName().trim();
        String[] nameParts = splitFilename(fileName);
        String baseName = nameParts[0];
        String suffix = nameParts[1];

        // ---- 5. 幂等：目标目录已有「同 hash + 同名」记录时直接复用 ----
        for (FileInfo sibling : fileInfoMapperService.findByParentIdAndUserId(currentUserId, parentId)) {
            if (sha256Hash.equals(sibling.getSha256Hash())
                    && baseName.equals(sibling.getFilename())
                    && Objects.equals(suffix, sibling.getFileSuffix())) {
                log.info("秒传幂等命中，复用已有记录：fileId={}, parentId={}", sibling.getId(), parentId);
                return Result.success(FastUploadVO.builder()
                        .fileId(sibling.getId())
                        .filename(fileName)
                        .fileSize(fileSize)
                        .reused(true)
                        .build());
            }
        }

        // ---- 6. 落库：防重名、分类都在 createReusedRecord 内完成 ----
        FileInfo newFileInfo = createReusedRecord(fileName, fileSize, existingFile, sha256Hash,
                parentId, request.getTaskId(), currentUserId);

        log.info("秒传成功：userId={}, fileId={}, filename={}, size={}",
                currentUserId, newFileInfo.getId(), newFileInfo.getFilename(), fileSize);

        String resolvedSuffix = newFileInfo.getFileSuffix();
        return Result.success(FastUploadVO.builder()
                .fileId(newFileInfo.getId())
                .filename(resolvedSuffix == null || resolvedSuffix.isEmpty()
                        ? newFileInfo.getFilename()
                        : newFileInfo.getFilename() + "." + resolvedSuffix)
                .fileSize(newFileInfo.getFileSize())
                .reused(false)
                .build());
    }

    /**
     * 秒传落库：复用已有物理对象的存储路径，只新增一条文件记录（不重复占 OSS 空间）。
     *
     * @return 新生成的文件记录
     */
    private FileInfo createReusedRecord(String originalFilename, long fileSize, FileInfo existingFile,
                                        String sha256Hash, Long parentId, String taskId, Long currentUserId) {
        String[] nameParts = splitFilename(originalFilename);
        String[] resolved = resolveUploadNameConflict(nameParts[0], nameParts[1], parentId, currentUserId);
        String finalBaseName = resolved[0];
        String finalSuffix = resolved[1];
        String resolvedFilename = joinFilename(finalBaseName, finalSuffix);

        FileInfo newFileInfo = fileMapper.toFileInfoForFastUpload(resolvedFilename, finalBaseName,
                existingFile, currentUserId, parentId, sha256Hash);
        // toFileInfoForFastUpload 未映射 fileSuffix/localFilePath/categoryId，必须在此补齐
        newFileInfo.setFileSuffix(finalSuffix);
        newFileInfo.setCategoryId(FileTypeUtil.getCategoryId(finalSuffix));
        newFileInfo.setLocalFilePath(resolveLocalFilePath(parentId, resolvedFilename, currentUserId));
        fileInfoMapperService.save(newFileInfo);

        if (taskId != null) {
            // 秒传没有真实字节传输，直接把进度推到 100%
            upLoadProgressService.updateProgress(taskId, fileSize, fileSize);
        }
        return newFileInfo;
    }

    /**
     * 拆文件名：无后缀时 baseName 为原名、suffix 为空串。
     */
    private String[] splitFilename(String originalFilename) {
        int dotIdx = originalFilename == null ? -1 : originalFilename.lastIndexOf(".");
        String baseName = dotIdx > 0 ? originalFilename.substring(0, dotIdx) : originalFilename;
        String suffix = dotIdx > 0 ? originalFilename.substring(dotIdx + 1) : "";
        return new String[]{baseName, suffix};
    }

    private String joinFilename(String baseName, String suffix) {
        return (suffix == null || suffix.isEmpty()) ? baseName : baseName + "." + suffix;
    }

    /**
     * 同级目录重名时自动加序号：test.txt -> test(1).txt -> ...
     */
    private String[] resolveUploadNameConflict(String baseName, String suffix, Long parentId, Long currentUserId) {
        List<FileInfo> siblings = fileInfoMapperService.findByParentIdAndUserId(currentUserId, parentId);
        String resolvedBase = baseName;
        String resolvedSuffix = suffix;

        if (hasNameConflict(resolvedBase, resolvedSuffix, siblings)) {
            int counter = 1;
            while (hasNameConflict(baseName + "(" + counter + ")", resolvedSuffix, siblings) && counter < 1000) {
                counter++;
            }
            resolvedBase = baseName + "(" + counter + ")";
            log.info("秒传文件同名冲突，自动重命名：{}.{} -> {}.{}", baseName, suffix, resolvedBase, resolvedSuffix);
        }
        return new String[]{resolvedBase, resolvedSuffix};
    }

    private boolean hasNameConflict(String name, String suffix, List<FileInfo> siblings) {
        if (siblings == null) {
            return false;
        }
        String fullName = (suffix != null && !suffix.isEmpty()) ? name + "." + suffix : name;
        for (FileInfo file : siblings) {
            String siblingName = (file.getFileSuffix() != null && !file.getFileSuffix().isEmpty())
                    ? file.getFilename() + "." + file.getFileSuffix()
                    : file.getFilename();
            if (fullName.equalsIgnoreCase(siblingName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算虚拟目录路径：父目录本地路径 + 解析后的文件名；根目录返回空串。
     */
    private String resolveLocalFilePath(Long parentId, String resolvedFilename, Long currentUserId) {
        if (parentId == null || parentId == 0L) {
            return "";
        }
        FileInfo parent = fileInfoMapperService.findByIdAndUserId(parentId, currentUserId);
        return (parent != null && parent.getLocalFilePath() != null)
                ? parent.getLocalFilePath() + resolvedFilename : "";
    }
}
