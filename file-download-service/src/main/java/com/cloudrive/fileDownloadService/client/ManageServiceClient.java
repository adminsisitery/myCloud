package com.cloudrive.fileDownloadService.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * file-manage-service 内部客户端
 * <p>
 * 单文件下载开始前，回调文件管理服务写入「最近访问」记录（t_recent_access）。
 * 最近访问属于文件管理域数据，下载服务不直接写管理服务的表。
 * 该回调失败只记日志，不阻断下载主流程。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Component
public class ManageServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(ManageServiceClient.class);

    private final RestClient restClient;

    public ManageServiceClient(
            @Value("${file-manage-service.base-url:http://localhost:8084}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 写入最近访问记录
     *
     * @param userId 用户 ID
     * @param fileId 文件 ID
     */
    public void recordAccess(long userId, long fileId) {
        try {
            restClient.post()
                    .uri("/internal/recent/record?userId={userId}&fileId={fileId}", userId, fileId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            logger.warn("记录最近访问失败（不阻断下载）: userId={}, fileId={}", userId, fileId, e);
        }
    }
}
