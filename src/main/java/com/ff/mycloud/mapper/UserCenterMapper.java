package com.ff.mycloud.mapper;

import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.entity.UserCenter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author Lenovo
 */
@Mapper(componentModel = "spring")
public interface UserCenterMapper {

    @Mapping(target = "username",source = "user.username")
    @Mapping(target = "createTime",source = "user.createTime")
    @Mapping(target = "usedSpace",source = "usedSpace")
    @Mapping(target = "totalSpace",source = "totalSpace")
    @Mapping(target = "userId",source = "user.id")
    UserCenter UserToUserCenter(User user,double usedSpace,double totalSpace);
}
