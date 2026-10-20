package com.cloudrive.fileManageService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * user-service 空间配额内部客户端（占位实现）。
 * <p>
 * 远程调用实现已清空，由业务方自行实现。对应 user-service 内部接口：
 * POST /internal/space/reserve、/release、/add。
 * 当前占位实现不发起任何 HTTP 调用：预占恒返回 true、释放/增加为空操作。
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
     * @param userId   用户 ID
     * @param fileSize 预占字节数
     * @return 占位实现恒返回 true
     */
    //远程调用待实现：user-service POST /internal/space/reserve
    @PostMapping("/reserve")
    boolean reserveSpace(long userId, long fileSize);

    /**
     * 释放用户已用空间（占位）。
     *
     * @param userId 用户 ID
     * @param size   释放字节数
     */
    //远程调用待实现：user-service POST /internal/space/release
    @PostMapping("/release")
    void releaseSpace(long userId, long size);

    /**
     * 增加用户已用空间（占位）。
     *
     * @param userId 用户 ID
     * @param size   增加字节数
     */
    // 远程调用待实现：user-service POST /internal/space/add
    @PostMapping("/add")
    void addUsedSpace(long userId, long size);
}
