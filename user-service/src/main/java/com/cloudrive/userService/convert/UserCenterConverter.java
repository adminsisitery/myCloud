package com.cloudrive.userService.convert;

import com.cloudrive.userService.domain.po.User;
import com.cloudrive.userService.domain.po.UserCenter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 用户实体 → 用户中心实体的转换器（MapStruct）。
 * <p>注册时由用户信息初始化配额记录。</p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper(componentModel = "spring")
public interface UserCenterConverter {

    /**
     * 用用户信息与配额构造用户中心记录。
     *
     * @param user       用户实体
     * @param usedSpace  已使用空间（字节）
     * @param totalSpace 总空间配额（字节）
     * @return 用户中心实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "createTime", source = "user.createTime")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "usedSpace", source = "usedSpace")
    @Mapping(target = "totalSpace", source = "totalSpace")
    UserCenter toUserCenter(User user, long usedSpace, long totalSpace);
}
