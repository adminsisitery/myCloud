package com.ff.mycloud.Controller;


import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.constant.ShareConstants;
import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.model.dto.ShareAccessDTO;
import com.ff.mycloud.model.dto.ShareCreateDTO;
import com.ff.mycloud.model.vo.ShareFileVO;
import com.ff.mycloud.service.impl.shareServiceImpl;
import com.ff.mycloud.service.shareService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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


    public ShareController(com.ff.mycloud.service.impl.shareServiceImpl shareServiceImpl) {
        this.shareServiceImpl = shareServiceImpl;
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
    public ResponseEntity<byte[]> downloadSharedFile(@PathVariable String shareCode) {
        // 从Cookie中获取令牌
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
        String token = extractShareTokenFromCookies(shareCode, request);
        byte[] file = shareServiceImpl.downloadSharedFile(shareCode,token);
        // 设置响应头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", shareServiceImpl.getFilename(shareCode));
        headers.setContentLength(file.length);
        return ResponseEntity.ok()
                .headers(headers)
                .body(file);
    }
}
