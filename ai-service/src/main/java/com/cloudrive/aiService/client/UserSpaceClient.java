package com.cloudrive.aiService.client;

import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 空间配额跨服务客户端
 * <p>
 * 调用 user-service 的内网接口 {@code /internal/space/**}，避免 ai-service 直接写
 * {@code t_user_center} 导致空间数据多写者。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Component
public class UserSpaceClient {

    private static final Logger log = LoggerFactory.getLogger(UserSpaceClient.class);

    private final RestClient restClient;

    public UserSpaceClient(@Value("${user-service.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 预占空间
     *
     * @param userId 用户ID
     * @param size   字节数
     * @return true 表示预占成功，false 表示空间不足
     */
    public boolean reserveSpace(long userId, long size) {
        try {
            Result<Boolean> result = restClient.post()
                    .uri(builder -> builder.path("/internal/space/reserve")
                            .queryParam("userId", userId)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Boolean>>() {
                    });
            return result != null && Boolean.TRUE.equals(result.getData());
        } catch (RestClientException e) {
            log.error("调用 user-service 预占空间失败: userId={}, size={}", userId, size, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 释放空间
     *
     * @param userId 用户ID
     * @param size   字节数
     */
    public void releaseSpace(long userId, long size) {
        try {
            restClient.post()
                    .uri(builder -> builder.path("/internal/space/release")
                            .queryParam("userId", userId)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("调用 user-service 释放空间失败: userId={}, size={}", userId, size, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 增加已用空间（回收站恢复场景，不校验配额）
     *
     * @param userId 用户ID
     * @param size   字节数
     */
    public void addUsedSpace(long userId, long size) {
        try {
            restClient.post()
                    .uri(builder -> builder.path("/internal/space/add")
                            .queryParam("userId", userId)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("调用 user-service 增加已用空间失败: userId={}, size={}", userId, size, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }
}
