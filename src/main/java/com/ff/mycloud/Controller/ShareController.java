package com.ff.mycloud.Controller;


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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @description: TODO
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


    public ShareController(com.ff.mycloud.service.impl.shareServiceImpl shareServiceImpl, FileInfoMapperService fileInfoMapperService, ShareRecordMapperServic shareRecordMapperService, FolderService folderService) {
        this.shareServiceImpl = shareServiceImpl;
        this.fileInfoMapperService = fileInfoMapperService;
        this.shareRecordMapperService = shareRecordMapperService;
        this.folderService = folderService;
    }

    @PostMapping
    public Result<ShareFileVO> createShareFile(@RequestBody @Validated ShareCreateDTO dto) {
        ShareFileVO share = shareServiceImpl.createShare(
                dto.getFileId(),
                dto.getExpireTime(),
                dto.getPassword()
        );
        return Result.success(share);
    }

    /**
     * 获取当前用户的所有分享
     */

    @GetMapping
    public Result<List<ShareFileVO>> getShareFiles() {
        List<ShareFileVO> sharedFiles = shareServiceImpl.getSharedFiles();
        return Result.success(sharedFiles);
    }



//    @PostMapping("/{sharCode}")
//    public Result<ShareFileVO> getShareFile(@PathVariable String sharCode
//    , @Valid @RequestBody(required = false) ShareAccessDTO dto) {
//
//    }

    @DeleteMapping("/{shareCode}")
    public Result<Void> cancelShare(@PathVariable String shareCode) {
        shareServiceImpl.cancelShare(shareCode);
        return Result.success();
    }

    @PostMapping("/{shareCode}")
    public Result<ShareFileVO> getShare(@PathVariable String shareCode,
                                        @RequestBody(required = false) @Valid ShareCreateDTO dto) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = extractShareTokenFromCookies(shareCode,request);

        if (token!=null&&shareServiceImpl.validateShareToken(shareCode,token)){
            log.debug("Valid token found for share code: {}", shareCode);
            try{
                ShareFileVO shareFileVO = shareServiceImpl.accessShareByToken(shareCode, token);
                return Result.success(shareFileVO);
            }catch (BusinessException e){

            }
        }
        String password = dto !=null ? dto.getPassword() : null;
        ShareFileVO shareFileVO = shareServiceImpl.accessShare(shareCode, password);
        return Result.success(shareFileVO);
    }
    @PostMapping("/{shareCode}/verification")
    public Result<ShareFileVO> verifyShare(@PathVariable String shareCode, @Valid @RequestBody ShareAccessDTO dto){
        // 验证密码并获取分享信息
        ShareFileVO shareFileVO = shareServiceImpl.accessShare(shareCode, dto.getPassword());
        // 生成分享令牌
        String token = shareServiceImpl.generateShareToken(shareCode, dto.getPassword());
        // 创建分享特定的Cookie
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getResponse();
        ResponseCookie responseCookie= ResponseCookie.from( // 改fromClientResponse为from
                        ShareConstants.Token.SHARE_TOKEN_COOKIE_PREFIX + shareCode,
                        token
                )
                .secure(false) // 本地http环境下关闭secure（https才开true）
                .path("/")
                .maxAge(CommonConstants.Time.ONE_DAY)
                .httpOnly(false) // 前端要读取Cookie需关闭
                .sameSite("Lax")
                .build();
        // ==============================================
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        return Result.success(shareFileVO);
    }

    private String extractShareTokenFromCookies(String shareCode, HttpServletRequest request) {

        if (shareCode == null||request==null) {
            log.warn("Invalid parameters: shareCode={}, request={}", shareCode, request);
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            log.warn("Invalid parameters: cookies is null");
            return null;
        }
        String cookieName = ShareConstants.Token.SHARE_TOKEN_COOKIE_PREFIX+shareCode;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @GetMapping("/{shareCode}/content")
    public ResponseEntity<byte[]> downloadSharedFile(@PathVariable String shareCode, HttpServletResponse response) {
        // 从Cookie中获取令牌
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
        String token = extractShareTokenFromCookies(shareCode, request);

        FileInfo fileInfoByShare = getFileInfoByShare(shareCode, token);
        if (fileInfoByShare.getIsFolder()) {
            //是文件夹
            folderService.downLoadFolder(fileInfoByShare.getId(),response);
        } else {
            byte[] file = shareServiceImpl.downloadSharedFile(shareCode, token);
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", shareServiceImpl.getFilename(shareCode));
            headers.setContentLength(file.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(file);
        }
        return null;
    }
    public FileInfo getFileInfoByShare(String shareCode, String token) {
        // 这里复用你现有的分享验证逻辑
        // 1. 验证 shareCode 是否存在
        // 新增：打印关键参数
        System.out.println("收到 shareCode: " + shareCode);
        System.out.println("收到 token: " + token);

        // 1. 验证 shareCode 是否存在
        ShareRecord shareRecord = shareRecordMapperService.findByShareCode(shareCode);

        // 新增：打印查询结果
        System.out.println("数据库查到的 shareRecord: " + shareRecord);
        if (shareRecord != null) {
            System.out.println("shareRecord.isExpired() 字段值: " + shareRecord.getIsExpired());
            System.out.println("shareRecord.expireTime: " + shareRecord.getExpireTime());
            System.out.println("当前时间: " + LocalDateTime.now());
            System.out.println("是否真的过期(逻辑判断): " + shareRecord.getExpireTime().isBefore(LocalDateTime.now()));
        }

        if (shareRecord == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }

        // 2. 验证 token（如果有密码）
        if (StringUtil.isNotBlank(shareRecord.getPassword())) {
            // 这里复用你现有的 token 验证逻辑
            shareServiceImpl.validateShareToken(shareCode, token);
        }

        // 3. 验证是否过期
        if (shareRecord.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }

        // 4. 获取文件信息并验证原文件是否存在
        FileInfo fileInfo = fileInfoMapperService.getById(shareRecord.getFileId());
        if (fileInfo == null || fileInfo.getIsDeleted()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        return fileInfo;
    }
}
