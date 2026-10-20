package com.cloudrive.fileDownloadService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * file-manage-service 内部客户端。
 * <p>
 * 单文件下载开始前，回调文件管理服务写入「最近访问」记录（t_recent_access）。
 * 最近访问属于文件管理域数据，下载服务不直接写管理服务的表。
 * {@link FeignClient} 已负责注册 Bean，不需要再加 {@code @Component}。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@FeignClient(name = "file-manage-service", path = "/internal/recent")
public interface ManageServiceClient {

    /**
     * 写入最近访问记录。
     *
     * @param userId 用户 ID
     * @param fileId 文件 ID
     */
    @PostMapping("/record")
    void recordAccess(@RequestParam("userId") long userId, @RequestParam("fileId") long fileId);
}
