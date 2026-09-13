package com.ff.mycloud.Controller;


import cn.hutool.core.util.StrUtil;
import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.constant.ShareConstants;
import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.model.dto.ShareAccessDTO;
import com.ff.mycloud.model.dto.ShareCreateDTO;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.ShareRecord;
import com.ff.mycloud.model.vo.ShareFileVO;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FolderService;
import com.ff.mycloud.service.ShareRecordMapperServic;
import com.ff.mycloud.service.impl.ShareRecordMapperServicImpl;
import com.ff.mycloud.service.impl.shareServiceImpl;
import com.ff.mycloud.service.shareService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件分享控制器
 * <p>
 * 提供文件的分享功能，包括创建分享链接、获取分享列表、取消分享、验证分享密码、
 * 获取分享内容以及下载分享文件。分享支持密码保护和 Token（Cookie）机制，
 * 验证通过后可在有效期内免密访问。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/5 14:08
 * @version 1.0
 */

@Slf4j
@RestController
@Validated
@RequestMapping("/api/shares")
public class ShareController {

    shareServiceImpl shareServiceImpl;
    FileInfoMapperService fileInfoMapperService;
    ShareRecordMapperServic shareRecordMapperService;
    FolderService folderService;


    /**
     * 构造器注入分享相关的服务依赖
     *
     * @param shareServiceImpl         分享服务实现
     * @param fileInfoMapperService    文件信息 Mapper 服务
     * @param shareRecordMapperService 分享记录 Mapper 服务
     * @param folderService            文件夹服务（用于下载分享的文件夹）
     */
    public ShareController(com.ff.mycloud.service.impl.shareServiceImpl shareServiceImpl, FileInfoMapperService fileInfoMapperService, ShareRecordMapperServic shareRecordMapperService, FolderService folderService) {
        this.shareServiceImpl = shareServiceImpl;
        this.fileInfoMapperService = fileInfoMapperService;
        this.shareRecordMapperService = shareRecordMapperService;
        this.folderService = folderService;
    }

    /**
     * 创建文件分享
     * <p>
     * 对指定文件创建分享链接，可设置过期时间和访问密码。
     * </p>
     *
     * @param dto 包含 fileId、expireTime 和 password 的创建 DTO
     * @return 包含分享链接和提取码的 VO
     */
    @PostMapping
    @RateLimit(dimensions = { RateLimit.Dimension.USER, RateLimit.Dimension.IP }, permitsPerSecond = 5.0, timeout = 1000)
    public Result<ShareFileVO> createShareFile(@RequestBody @Validated ShareCreateDTO dto) {
        ShareFileVO share = shareServiceImpl.createShare(
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
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 10.0, timeout = 500)
    public Result<List<ShareFileVO>> getShareFiles() {
        List<ShareFileVO> sharedFiles = shareServiceImpl.getSharedFiles();
        return Result.success(sharedFiles);
    }



//    @PostMapping("/{sharCode}")
//    public Result<ShareFileVO> getShareFile(@PathVariable String sharCode
//    , @Valid @RequestBody(required = false) ShareAccessDTO dto) {
//
//    }

    /**
     * 取消分享
     * <p>
     * 根据分享码（shareCode）取消分享，使该分享链接失效。
     * </p>
     *
     * @param shareCode 分享码
     * @return 操作结果
     */
    @DeleteMapping("/{shareCode}")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> cancelShare(@PathVariable String shareCode) {
        shareServiceImpl.cancelShare(shareCode);
        return Result.success();
    }

    /**
     * 访问分享内容
     * <p>
     * 优先尝试从 Cookie 中提取分享 Token 进行免密访问；
     * 如果 Token 不存在或无效，则使用请求体中的密码进行验证。
     * </p>
     *
     * @param shareCode 分享码
     * @param dto       可选的包含密码的 DTO
     * @return 分享文件 VO
     */
    @PostMapping("/{shareCode}")
    @RateLimit(dimensions = { RateLimit.Dimension.IP }, permitsPerSecond = 10.0, timeout = 1000)
    public Result<ShareFileVO> getShare(@PathVariable String shareCode,
                                        @RequestBody(required = false) @Valid ShareCreateDTO dto) {
        // 获取当前 HTTP 请求对象
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        // 从 Cookie 中提取分享令牌
        String token = extractShareTokenFromCookies(shareCode,request);

        // 如果令牌有效，直接通过令牌访问分享
        if (token!=null&&shareServiceImpl.validateShareToken(shareCode,token)){
            log.debug("分享访问凭证校验通过");
            try{
                ShareFileVO shareFileVO = shareServiceImpl.accessShareByToken(shareCode, token);
                return Result.success(shareFileVO);
            }catch (BusinessException e){
                // 令牌访问失败，降级到密码验证流程
            }
        }
        // 使用密码访问分享
        String password = dto !=null ? dto.getPassword() : null;
        ShareFileVO shareFileVO = shareServiceImpl.accessShare(shareCode, password);
        return Result.success(shareFileVO);
    }

    /**
     * 验证分享密码并获取分享内容
     * <p>
     * 验证密码正确后，生成分享 Token 并写入 HttpOnly Cookie，
     * 后续访问可在有效期内免密。
     * </p>
     *
     * @param shareCode 分享码
     * @param dto       包含密码的验证 DTO
     * @return 分享文件 VO
     */
    @PostMapping("/{shareCode}/verification")
    @RateLimit(dimensions = { RateLimit.Dimension.IP }, permitsPerSecond = 3.0, timeout = 1000)
    public Result<ShareFileVO> verifyShare(@PathVariable String shareCode, @Valid @RequestBody ShareAccessDTO dto){
        // 验证密码并获取分享信息
        ShareFileVO shareFileVO = shareServiceImpl.accessShare(shareCode, dto.getPassword());
        // 生成分享令牌
        String token = shareServiceImpl.generateShareToken(shareCode, dto.getPassword());
        // 获取当前 HTTP 响应对象
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getResponse();
        // 构建分享专用的 Cookie（前缀+shareCode 作为 key，token 作为 value）
        ResponseCookie responseCookie= ResponseCookie.from(
                        ShareConstants.Token.SHARE_TOKEN_COOKIE_PREFIX + shareCode,
                        token
                )
                .secure(false) // 本地http环境下关闭secure（https才开true）
                .path("/")
                .maxAge(CommonConstants.Time.ONE_DAY) // Cookie 有效期 1 天
                .httpOnly(false) // 前端要读取Cookie需关闭 httpOnly
                .sameSite("Lax")
                .build();
        // 将 Cookie 添加到响应头
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        return Result.success(shareFileVO);
    }

    /**
     * 从 Cookie 中提取分享令牌
     * <p>
     * 根据 shareCode 构造 Cookie 名称（前缀 + shareCode），
     * 遍历请求中的所有 Cookie 查找匹配的令牌值。
     * </p>
     *
     * @param shareCode 分享码
     * @param request   HTTP 请求对象
     * @return 令牌值，如果未找到则返回 null
     */
    private String extractShareTokenFromCookies(String shareCode, HttpServletRequest request) {

        if (shareCode == null||request==null) {
            log.warn("分享访问参数无效");
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            log.warn("Invalid parameters: cookies is null");
            return null;
        }
        // 构造 Cookie 名称：前缀 + shareCode
        String cookieName = ShareConstants.Token.SHARE_TOKEN_COOKIE_PREFIX+shareCode;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 下载分享的文件或文件夹
     * <p>
     * 验证分享 Token 后，如果是单文件则返回文件字节流；
     * 如果是文件夹则打包为 ZIP 通过 response 流式输出。
     * </p>
     *
     * @param shareCode 分享码
     * @param response  HTTP 响应对象
     * @return 文件字节数组响应实体（文件夹下载时返回 null，因为已通过 response 写出）
     */
    @GetMapping("/{shareCode}/content")
    @RateLimit(dimensions = { RateLimit.Dimension.IP }, permitsPerSecond = 2.0, timeout = 1000)
    public void downloadSharedFile(@PathVariable String shareCode, HttpServletResponse response) {
        // 从 Cookie 中获取分享令牌
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
        String token = extractShareTokenFromCookies(shareCode, request);


        // 获取分享对应的文件信息并验证权限
        FileInfo fileInfoByShare = getFileInfoByShare(shareCode, token);
        if (fileInfoByShare.getIsFolder()) {
            // 文件夹：压缩打包后通过 response 输出
            folderService.downLoadSharedFolder(fileInfoByShare.getId(), fileInfoByShare.getUserId(), response);
        } else {
            // 单文件：直接写响应，避免与文件夹分支混用 null ResponseEntity
            byte[] file = shareServiceImpl.downloadSharedFile(shareCode, token);
            response.setContentType("application/octet-stream");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(shareServiceImpl.getFilename(shareCode), StandardCharsets.UTF_8)
                    .build().toString());
            response.setContentLengthLong(file.length);
            try {
                response.getOutputStream().write(file);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED, "分享文件下载失败");
            }
        }
    }

    /**
     * 根据分享码和令牌获取分享对应的文件信息
     * <p>
     * 执行完整的分享验证链：检查分享是否存在 -> 验证密码/Toke n -> 检查是否过期 -> 检查原文件是否被删除。
     * </p>
     *
     * @param shareCode 分享码
     * @param token     分享令牌（如果分享有密码）
     * @return 文件信息实体
     * @throws BusinessException 当分享不存在、令牌无效、已过期或文件已被删除时抛出
     */
    public FileInfo getFileInfoByShare(String shareCode, String token) {
        // 1. 验证 shareCode 是否存在
        ShareRecord shareRecord = shareRecordMapperService.findByShareCode(shareCode);

        if (shareRecord == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }

        // 2. 如果有密码，验证 token 是否有效
        if (StringUtil.isNotBlank(shareRecord.getPassword())) {
            if (!shareServiceImpl.validateShareToken(shareCode, token)) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
        }

        // 3. 检查分享是否过期
        if (shareRecord.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }

        // 4. 验证原文件是否存在且未被删除
        FileInfo fileInfo = fileInfoMapperService.getById(shareRecord.getFileId());
        if (fileInfo == null || fileInfo.getIsDeleted()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        return fileInfo;
    }
}
