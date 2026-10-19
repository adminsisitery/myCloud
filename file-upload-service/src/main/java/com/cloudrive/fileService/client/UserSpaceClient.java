package com.cloudrive.fileService.client;

import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.enums.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 用户空间服务客户端。
 * <p>
 * 空间配额（usedSpace/totalSpace）的归属在用户服务，文件服务不再直接写用户中心的表，
 * 而是通过 HTTP 调用 user-service 的内部接口完成"预占/释放"两个原子操作。
 * 两个服务注册到同一个 Nacos，生产环境可按需替换为服务发现 + 负载均衡。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Component
public class UserSpaceClient {

    private final RestClient restClient;

    public UserSpaceClient(@Value("${user-service.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 预占用户空间（原子检查并扣减可用空间）。
     *
     * @param userId   用户ID
     * @param fileSize 需要预留的空间大小（字节）
     * @return true表示空间预留成功，false表示空间不足
     * @throws BusinessException 用户服务不可达时抛出
     */
    public boolean reserveSpace(long userId, long fileSize) {
        try {
            SpaceResponse resp = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/internal/space/reserve")
                            .queryParam("userId", userId)
                            .queryParam("size", fileSize)
                            .build())
                    .retrieve()
                    .body(SpaceResponse.class);
            return resp != null && Boolean.TRUE.equals(resp.data());
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "空间服务不可用，请稍后重试");
        }
    }

    /**
     * 释放用户已占用空间。
     *
     * @param userId 用户ID
     * @param size   要释放的空间大小（字节）
     * @throws BusinessException 用户服务不可达时抛出
     */
    public void releaseSpace(long userId, long size) {
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/internal/space/release")
                            .queryParam("userId", userId)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "空间服务不可用，请稍后重试");
        }
    }

    /**
     * user-service 统一返回体 {@code Result<Boolean>} 的最小镜像，
     * 避免对公共库 Result 的构造器形状产生反序列化依赖。
     */
    private record SpaceResponse(Integer code, String message, Boolean data) {
    }
}
