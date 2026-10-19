package com.cloudrive.fileService.service;

import com.cloudrive.fileService.model.dto.FastUploadRequest;
import com.cloudrive.fileService.model.entity.FileCheck;
import com.cloudrive.fileService.model.vo.FastUploadVO;
import com.cloudrive.fileService.model.vo.FileCheckVO;
import com.cloudrive.pan_common.result.Result;

/**
 * 文件秒传服务
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.service.FileService#checkFilesHash} 与
 * {@code #fastUpload}，拆成「判定（无副作用）」与「执行（落库）」两步。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileFastUploadService {

    /**
     * 秒传判定（只判定，不落库）
     *
     * @param fileCheck 文件校验参数（sha256Hash 与 fileSize 缺一即判为不可秒传）
     * @return 判定结果，exists=true 表示可秒传
     */
    Result<FileCheckVO> checkFilesHash(FileCheck fileCheck);

    /**
     * 执行秒传（落库）
     *
     * @param request 秒传请求（文件名、大小、SHA-256、目标目录）
     * @return 落库结果（fileId、最终文件名、是否幂等复用）
     */
    Result<FastUploadVO> fastUpload(FastUploadRequest request);
}
