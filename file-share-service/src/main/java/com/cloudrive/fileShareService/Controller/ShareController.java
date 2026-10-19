package com.cloudrive.fileShareService.Controller;

import com.cloudrive.fileShareService.common.ShareConstants;
import com.cloudrive.fileShareService.model.dto.ShareAccessDTO;
import com.cloudrive.fileShareService.model.dto.ShareCreateDTO;
import com.cloudrive.fileShareService.model.entity.FileInfo;
import com.cloudrive.fileShareService.model.entity.ShareRecord;
import com.cloudrive.fileShareService.model.vo.ShareFileVO;
import com.cloudrive.fileShareService.service.FileInfoMapperService;
import com.cloudrive.fileShareService.service.FileShareService;
import com.cloudrive.fileShareService.service.FolderShareService;
import com.cloudrive.fileShareService.service.ShareRecordMapperService;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.result.Result;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件分享控制器
 * <p>
 * 从原单体 {@code ShareController} 迁移：创建分享、分享列表、取消分享、
 * 密码/Token 校验、分享内容与分享文件夹打包下载。接口路径与请求/响应契约保持不变。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Slf4j
@RestController
@Validated
@RequestMapping("/api/shares")
public class ShareController {

    private final FileShareService fileShareService;
    private final FileInfoMapperService fileInfoMapperService;
    private final ShareRecordMapperService shareRecordMapperService;
    private final FolderShareService folderShareService;

    public ShareController(FileShareService fileShareService,
                           FileInfoMapperService fileInfoMapperService,
                           ShareRecordMapperService shareRecordMapperService,
                           FolderShareService folderShareService) {
        this.fileShareService = fileShareService;
        this.fileInfoMapperService = fileInfoMapperService;
        this.shareRecordMapperService = shareRecordMapperService;
        this.folderShareService = folderShareService;
    }

    /**
     * 创建文件分享
     *
     * @param dto 包含 fileId、expireTime 和 password 的创建 DTO
     * @return 包含分享链接和提取码的 VO
     */
    @PostMapping
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 5.0, timeout = 1000)
    public Result<ShareFileVO> createShareFile(@RequestBody @Validated ShareCreateDTO dto) {
        ShareFileVO share = fileShareService.createShare(
                dto.getFileId(),
                dto.getExpireTime(),
                dto.getPassword()
        );
        return Result.success(share);
    }

    /**
     * 获取当前用户的所有分享列表
     *
     * @return 分享文件 VO 列表
     */
    @GetMapping
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 10.0, timeout = 500)
    public Result<List<ShareFileVO>> getShareFiles() {
        List<ShareFileVO> sharedFiles = fileShareService.getSharedFiles();
        return Result.success(sharedFiles);
    }

    /**
     * 取消分享
     *
     * @param shareCode 分享码
     * @return 操作结果
     */
    @DeleteMapping("/{shareCode}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> cancelShare(@PathVariable String shareCode) {
        fileShareService.cancelShare(shareCode);
        return Result.success();
    }

    /**
     * 访问分享内容
     * <p>优先从 Cookie 中提取分享 Token 免密访问，无效时降级为请求体密码验证。</p>
     *
     * @param shareCode 分享码
     * @param dto       可选的包含密码的 DTO
     * @return 分享文件 VO
     */
    @PostMapping("/{shareCode}")
    @RateLimit(dimensions = {RateLimit.Dimension.IP}, permitsPerSecond = 10.0, timeout = 1000)
    public Result<ShareFileVO> getShare(@PathVariable String shareCode,
                                        @RequestBody(required = false) @Valid ShareCreateDTO dto) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
        String token = extractShareTokenFromCookies(shareCode, request);
        if (token != null && fileShareService.validateShareToken(shareCode, token)) {
            try {
                return Result.success(fileShareService.accessShareByToken(shareCode, token));
            } catch (BusinessException e) {
                log.debug("Token 访问分享失败，降级为密码验证: shareCode={}", shareCode);
            }
        }
        String password = dto != null ? dto.getPassword() : null;
        return Result.success(fileShareService.accessShare(shareCode, password));
    }

    /**
     * 验证分享密码并获取分享内容（成功后下发 HttpOnly=false 的分享 Token Cookie）
     *
     * @param shareCode 分享码
     * @param dto       包含密码的访问 DTO
     * @return 分享文件 VO
     */
    @PostMapping("/{shareCode}/verification")
    @RateLimit(dimensions = {RateLimit.Dimension.IP}, permitsPerSecond = 3.0, timeout = 1000)
    public Result<ShareFileVO> verifyShare(@PathVariable String shareCode,
                                           @Valid @RequestBody ShareAccessDTO dto) {
        ShareFileVO shareFileVO = fileShareService.accessShare(shareCode, dto.getPassword());
        String token = fileShareService.generateShareToken(shareCode, dto.getPassword());
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getResponse();
        ResponseCookie responseCookie = ResponseCookie.from(
                        ShareConstants.Token.SHARE_TOKEN_COOKIE_PREFIX + shareCode, token)
                .secure(false)
                .path("/")
                .maxAge(ShareConstants.Token.EXPIRE_TIME)
                .httpOnly(false)
                .sameSite("Lax")
                .build();
        if (response != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        }
        return Result.success(shareFileVO);
    }

    /**
     * 从 Cookie 中提取分享令牌
     *
     * @param shareCode 分享码
     * @param request   HTTP 请求
     * @return 令牌值，未找到返回 null
     */
    private String extractShareTokenFromCookies(String shareCode, HttpServletRequest request) {
        if (shareCode == null || request == null) {
            log.warn("分享访问参数无效");
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String cookieName = ShareConstants.Token.SHARE_TOKEN_COOKIE_PREFIX + shareCode;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 下载分享的文件或文件夹
     * <p>验证分享 Token 后：单文件返回字节流，文件夹打包为 ZIP 通过 response 流式输出。</p>
     *
     * @param shareCode 分享码
     * @param response  HTTP 响应
     */
    @GetMapping("/{shareCode}/content")
    @RateLimit(dimensions = {RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public void downloadSharedFile(@PathVariable String shareCode, HttpServletResponse response) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
        String token = extractShareTokenFromCookies(shareCode, request);

        FileInfo fileInfo = getFileInfoByShare(shareCode, token);
        if (Boolean.TRUE.equals(fileInfo.getIsFolder())) {
            folderShareService.downLoadSharedFolder(fileInfo.getId(), fileInfo.getUserId(), response);
            return;
        }
        byte[] file = fileShareService.downloadSharedFile(shareCode, token);
        response.setContentType("application/octet-stream");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(fileShareService.getFilename(shareCode), StandardCharsets.UTF_8)
                .build().toString());
        response.setContentLengthLong(file.length);
        try {
            response.getOutputStream().write(file);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED, "分享文件下载失败");
        }
    }

    /**
     * 根据分享码和令牌获取分享对应的文件信息
     * <p>完整校验链：分享存在 -> 有密码时 Token 有效 -> 未过期 -> 原文件存在且未删除。</p>
     *
     * @param shareCode 分享码
     * @param token     分享令牌（有密码分享时必填）
     * @return 文件信息实体
     */
    public FileInfo getFileInfoByShare(String shareCode, String token) {
        ShareRecord shareRecord = shareRecordMapperService.findByShareCode(shareCode);
        if (shareRecord == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        if (shareRecord.getPassword() != null && !shareRecord.getPassword().trim().isEmpty()
                && !fileShareService.validateShareToken(shareCode, token)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (shareRecord.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }
        FileInfo fileInfo = fileInfoMapperService.getById(shareRecord.getFileId());
        if (fileInfo == null || Boolean.TRUE.equals(fileInfo.getIsDeleted())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return fileInfo;
    }
}
