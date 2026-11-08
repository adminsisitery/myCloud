package com.cloudrive.fileDownloadService.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 最近访问回调的熔断降级。
 * <p>
 * 这条调用本身只是旁路埋点，失败不影响下载主流程，降级时丢弃并记日志即可。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
@Slf4j
@Component
public class ManageServiceClientFallbackFactory implements FallbackFactory<ManageServiceClient> {

    @Override
    public ManageServiceClient create(Throwable cause) {
        return (userId, fileId) ->
                log.warn("最近访问写入熔断降级 userId={} fileId={} 原因={}", userId, fileId, cause.toString());
    }
}
