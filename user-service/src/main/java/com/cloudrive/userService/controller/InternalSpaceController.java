package com.cloudrive.userService.controller;

import com.cloudrive.pan_common.domain.user.SpaceUsage;
import com.cloudrive.pan_common.result.Result;
import com.cloudrive.userService.domain.po.UserCenter;
import com.cloudrive.userService.service.UserCenterMapperService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 空间配额内部接口。
 * <p>
 * 供 file-upload-service 等兄弟服务调用，完成"预占/释放"两个原子操作。
 * 空间配额的归属在用户服务，其他服务不允许直接写用户中心表——
 * 这是拆分时定下的数据归属边界。
 * </p>
 * <p>
 * 安全说明：路径以 /internal 开头，网关没有对应路由，外部流量打不进来；
 * 服务间调用由 pan-common 的 {@code InternalAuthWebConfig} 统一鉴权
 * （校验 X-Internal-Token 与时间戳，防重放），调用方由 Feign 拦截器自动注入请求头。
 * 未配置 {@code pan.internal.token} 时一律 401，避免漏配导致接口裸奔。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@RestController
@RequestMapping("/internal/space")
public class InternalSpaceController {

    private final UserCenterMapperService userCenterMapperService;

    public InternalSpaceController(UserCenterMapperService userCenterMapperService) {
        this.userCenterMapperService = userCenterMapperService;
    }

    /**
     * 预占用户空间（原子检查并扣减可用空间）。
     *
     * @param userId 用户ID
     * @param size   需要预留的空间大小（字节）
     * @return true表示空间预留成功，false表示空间不足
     */
    @PostMapping("/reserve")
    public Result<Boolean> reserve(@RequestParam("userId") Long userId,
                                   @RequestParam("size") Long size) {
        return Result.success(userCenterMapperService.reserveSpace(userId, size));
    }

    /**
     * 释放用户已占用空间。
     *
     * @param userId 用户ID
     * @param size   要释放的空间大小（字节）
     */
    @PostMapping("/release")
    public Result<Void> release(@RequestParam("userId") Long userId,
                                @RequestParam("size") Long size) {
        userCenterMapperService.releaseSpace(userId, size);
        return Result.success();
    }

    /**
     * 查询用户空间用量（供 file-upload-service 判断是否触发用量提醒）。
     *
     * @param userId 用户ID
     * @return 已用空间与总配额
     */
    @GetMapping("/info")
    public Result<SpaceUsage> info(@RequestParam("userId") Long userId) {
        UserCenter userCenter = userCenterMapperService.getByUserId(userId);
        if (userCenter == null) {
            return Result.error(404, "用户空间信息不存在");
        }
        return Result.success(new SpaceUsage(userCenter.getUsedSpace(), userCenter.getTotalSpace()));
    }

    /**
     * 恢复用户已占用空间（回收站恢复文件时把空间占用加回来）。
     * <p>与 reserve 不同，此操作不检查剩余空间，与原单体恢复逻辑保持一致。</p>
     *
     * @param userId 用户ID
     * @param size   要恢复的空间大小（字节）
     */
    @PostMapping("/add")
    public Result<Void> add(@RequestParam("userId") Long userId,
                            @RequestParam("size") Long size) {
        userCenterMapperService.addUsedSpace(userId, size);
        return Result.success();
    }
}
