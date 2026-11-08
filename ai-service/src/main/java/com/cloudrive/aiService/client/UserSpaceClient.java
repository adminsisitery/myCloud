package com.cloudrive.aiService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户空间配额远程调用客户端。
 * <p>
 * 调用 user-service 的内网接口 {@code /internal/space/**}，避免 ai-service 直接写
 * {@code t_user_center} 导致空间数据多写者。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@FeignClient(name = "user-service", path = "/internal/space",
        fallbackFactory = UserSpaceClientFallbackFactory.class)
public interface UserSpaceClient {

    /**
     * 预占空间。
     *
     * @param userId 用户ID
     * @param size   字节数
     * @return true 表示预占成功，false 表示空间不足
     */
    @PostMapping("/reserve")
    Boolean reserveSpace(@RequestParam("userId") long userId, @RequestParam("size") long size);

    /**
     * 释放空间。
     *
     * @param userId 用户ID
     * @param size   字节数
     */
    @PostMapping("/release")
    void releaseSpace(@RequestParam("userId") long userId, @RequestParam("size") long size);

    /**
     * 增加已用空间（回收站恢复场景，不校验配额）。
     *
     * @param userId 用户ID
     * @param size   字节数
     */
    @PostMapping("/add")
    void addUsedSpace(@RequestParam("userId") long userId, @RequestParam("size") long size);
}
