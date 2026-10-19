package com.cloudrive.pan_common.domain.user;

import java.io.Serializable;

/**
 * 用户空间用量（内部接口 user-service GET /internal/space/info 的响应体）
 *
 * @param usedSpace  已用空间（字节）
 * @param totalSpace 总空间配额（字节）
 * @author 飞飞
 * @date 2026/10/19
 */
public record SpaceUsage(long usedSpace, long totalSpace) implements Serializable {
}
