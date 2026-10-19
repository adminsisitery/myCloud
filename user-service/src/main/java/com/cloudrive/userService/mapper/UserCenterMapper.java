package com.cloudrive.userService.mapper;


import com.cloudrive.pan_common.domain.po.User;
import com.cloudrive.userService.domain.po.UserCenter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 功能描述：用户中心映射器，负责将用户实体与空间信息转换为用户中心实体。
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper(componentModel = "spring")
public interface UserCenterMapper {

    /**
     * 功能：将用户实体与空间使用信息转换为用户中心实体。
     *
     * @param user       用户实体
     * @param usedSpace  已使用空间（单位：字节）
     * @param totalSpace 总空间（单位：字节）
     * @return 用户中心实体
     */
    @Mapping(target = "username",source = "user.username")
    @Mapping(target = "createTime",source = "user.createTime")
    @Mapping(target = "usedSpace",source = "usedSpace")
    @Mapping(target = "totalSpace",source = "totalSpace")
    @Mapping(target = "userId",source = "user.id")
    UserCenter UserToUserCenter(User user, double usedSpace, double totalSpace);
}
