package com.cloudrive.fileManageService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户空间配额远程调用客户端。
 * <p>
 * 调用 user-service 的内网接口 {@code /internal/space/**}。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@FeignClient(name = "user-service", path = "/internal/space")
public interface UserSpaceClient {

    /**
     * 预占用户空间。
     *
     * @param userId   用户 ID
     * @param fileSize 预占字节数
     * @return true 表示预占成功
     */
    @PostMapping("/reserve")
    Boolean reserveSpace(@RequestParam("userId") long userId, @RequestParam("size") long fileSize);

    /**
     * 释放用户已用空间。
     *
     * @param userId 用户 ID
     * @param size   释放字节数
     */
    @PostMapping("/release")
    void releaseSpace(@RequestParam("userId") long userId, @RequestParam("size") long size);

    /**
     * 增加用户已用空间（回收站恢复场景，不校验配额）。
     *
     * @param userId 用户 ID
     * @param size   增加字节数
     */
    @PostMapping("/add")
    void addUsedSpace(@RequestParam("userId") long userId, @RequestParam("size") long size);
}
