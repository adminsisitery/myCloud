package com.cloudrive.fileService.client;

import com.cloudrive.pan_common.domain.user.SpaceUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户空间配额调用的熔断降级。
 * <p>
 * 熔断打开或调用异常时按安全侧处理：
 * 预占返回 false（宁可拒绝本次上传，也不能让配额超卖）；
 * 释放属于补偿操作，失败只记日志；配额查询返回 null，调用方已做判空。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
@Slf4j
@Component
public class UserSpaceClientFallbackFactory implements FallbackFactory<UserSpaceClient> {

    @Override
    public UserSpaceClient create(Throwable cause) {
        return new UserSpaceClient() {

            @Override
            public Boolean reserveSpace(long userId, long fileSize) {
                log.warn("空间预占熔断降级 userId={} size={} 原因={}", userId, fileSize, cause.toString());
                return false;
            }

            @Override
            public void releaseSpace(long userId, long size) {
                log.warn("空间释放熔断降级 userId={} size={} 原因={}", userId, size, cause.toString());
            }

            @Override
            public SpaceUsage getSpaceUsage(long userId) {
                log.warn("空间查询熔断降级 userId={} 原因={}", userId, cause.toString());
                return null;
            }
        };
    }
}
