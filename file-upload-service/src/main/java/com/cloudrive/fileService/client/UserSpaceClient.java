package com.cloudrive.fileService.client;

import com.cloudrive.pan_common.domain.user.SpaceUsage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 用户空间服务客户端（占位实现）。
 * <p>
 * 远程调用实现已清空，由业务方自行实现（例如 Feign / LoadBalanced RestClient / Dubbo）。
 * 对应 user-service 内部接口：
 * <ul>
 *     <li>POST /internal/space/reserve</li>
 *     <li>POST /internal/space/release</li>
 *     <li>GET  /internal/space/info</li>
 * </ul>
 * 当前占位实现不发起任何 HTTP 调用：预占恒返回 true、释放为空操作、用量查询返回 null。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Component
@FeignClient(name = "user-service",path = "/internal/space")
public interface UserSpaceClient {

    /**
     * 预占用户空间（占位）。
     *
     * @param userId   用户ID
     * @param fileSize 需要预留的空间大小（字节）
     * @return 占位实现恒返回 true
     */
    @PostMapping("/release")
    boolean reserveSpace(long userId, long fileSize);

    /**
     * 释放用户已占用空间（占位）。
     *
     * @param userId 用户ID
     * @param size   要释放的空间大小（字节）
     */
    @PostMapping("/release")
    void releaseSpace(long userId, long size);

    /**
     * 查询用户空间用量（占位）。
     *
     * @param userId 用户ID
     * @return 占位实现返回 null
     */
    @GetMapping("/info")
    SpaceUsage getSpaceUsage(long userId);
}
