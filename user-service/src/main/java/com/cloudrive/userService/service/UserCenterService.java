package com.cloudrive.userService.service;


import com.cloudrive.pan_common.result.Result;
import com.cloudrive.userService.domain.po.UserCenter;

/**
 * 用户中心服务接口
 * <p>提供用户中心信息的查询和密码修改功能。</p>
 *
 * @author 飞飞
 * @date 2026/3/8
 */
public interface UserCenterService {

    /**
     * 获取当前登录用户的中心信息（包含空间使用情况等）
     *
     * @return 当前用户的UserCenter实体
     */
    UserCenter getUserCenterInfo();

    /**
     * 修改当前用户的密码
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 操作结果
     */
    Result<String> changePassword(String oldPassword, String newPassword);
}
