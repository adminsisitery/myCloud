package com.ff.mycloud.service;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.entity.UserCenter;

public interface UserCenterService {

    UserCenter getUserCenterInfo();

    Result<String> changePassword(String oldPassword, String newPassword);
}
