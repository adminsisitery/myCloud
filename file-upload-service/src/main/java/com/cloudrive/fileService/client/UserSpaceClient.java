package com.cloudrive.fileService.client;

import com.cloudrive.pan_common.domain.user.SpaceUsage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户空间配额远程调用客户端。
 * <p>
 * 调用 user-service 的内网接口 {@code /internal/space/**}，避免上传服务直接写
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
     * 预占用户空间。
     *
     * @param userId   用户ID
     * @param fileSize 需要预留的空间大小（字节）
     * @return true 表示预占成功，false 表示空间不足
     */
    @PostMapping("/reserve")
    Boolean reserveSpace(@RequestParam("userId") long userId, @RequestParam("size") long fileSize);

    /**
     * 释放用户已占用空间。
     *
     * @param userId 用户ID
     * @param size   要释放的空间大小（字节）
     */
    @PostMapping("/release")
    void releaseSpace(@RequestParam("userId") long userId, @RequestParam("size") long size);

    /**
     * 查询用户空间用量。
     *
     * @param userId 用户ID
     * @return 空间用量
     */
    @GetMapping("/info")
    SpaceUsage getSpaceUsage(@RequestParam("userId") long userId);
}
