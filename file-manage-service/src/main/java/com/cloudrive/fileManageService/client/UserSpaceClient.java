package com.cloudrive.fileManageService.client;

import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * user-service 空间配额内部客户端
 * <p>
 * 文件管理服务不直接写 user_center 表，配额扣减/归还/恢复统一走 user-service 的
 * {@code /internal/space/*} 内部接口（与 file-upload-service 的 UserSpaceClient 同构）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Component
public class UserSpaceClient {

    private static final Logger logger = LoggerFactory.getLogger(UserSpaceClient.class);

    private final RestClient restClient;

    public UserSpaceClient(@Value("${user-service.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 预占用户空间（创建下载/上传等场景）
     *
     * @param userId   用户 ID
     * @param fileSize 预占字节数
     * @return true 表示预占成功，false 表示空间不足
     */
    public boolean reserveSpace(long userId, long fileSize) {
        try {
            Boolean result = restClient.post()
                    .uri("/internal/space/reserve?userId={userId}&size={size}", userId, fileSize)
                    .retrieve()
                    .body(Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (RestClientException e) {
            logger.error("调用 user-service 预占空间失败: userId={}, size={}", userId, fileSize, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 释放用户已用空间（删除文件/回收站删除时归还）
     *
     * @param userId 用户 ID
     * @param size   释放字节数
     */
    public void releaseSpace(long userId, long size) {
        try {
            restClient.post()
                    .uri("/internal/space/release?userId={userId}&size={size}", userId, size)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            logger.error("调用 user-service 释放空间失败: userId={}, size={}", userId, size, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 增加用户已用空间（从回收站恢复文件时占用回来）
     *
     * @param userId 用户 ID
     * @param size   增加字节数
     */
    public void addUsedSpace(long userId, long size) {
        try {
            restClient.post()
                    .uri("/internal/space/add?userId={userId}&size={size}", userId, size)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            logger.error("调用 user-service 恢复空间占用失败: userId={}, size={}", userId, size, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }
}
