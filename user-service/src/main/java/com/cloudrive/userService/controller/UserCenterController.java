package com.cloudrive.userService.controller;



import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.userService.domain.dto.ChangePasswordDTO;
import com.cloudrive.userService.domain.po.Result;
import com.cloudrive.userService.domain.po.UserCenter;
import com.cloudrive.userService.mapper.UserCenterMapper;
import com.cloudrive.userService.service.UserCenterService;
import com.cloudrive.userService.service.UserMapperService;
import org.springframework.web.bind.annotation.*;

/**
 * 用户中心控制器
 * <p>
 * 提供用户个人中心相关功能，包括查询用户存储空间信息和修改密码。
 * 所有接口均需要用户登录后才能访问。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/7 16:14
 * @version 1.0
 */

@RestController
@RequestMapping("/api/user")
public class UserCenterController {

    UserMapperService userMapperService;
    UserCenterMapper userCenterMapper;
    UserCenterService userCenterService;

    /**
     * 构造器注入用户相关服务依赖
     *
     * @param userMapperService 用户 Mapper 服务
     * @param userCenterMapper  用户中心 Mapper
     * @param userCenterService 用户中心服务
     */
    public UserCenterController(UserMapperService userMapperService, UserCenterMapper userCenterMapper, UserCenterService userCenterService) {
        this.userMapperService = userMapperService;
        this.userCenterMapper = userCenterMapper;
        this.userCenterService = userCenterService;
    }

    /**
     * 查询当前用户的云盘信息（存储空间、使用量等）
     *
     * @return 用户中心实体，包含 totalSpace、usedSpace 等信息
     */
    @GetMapping("/info")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 10.0, timeout = 500)
    public Result<UserCenter> info() {
        UserCenter userCenter = userCenterService.getUserCenterInfo();
        return Result.success(userCenter);
    }

    /**
     * 修改当前用户密码
     * <p>
     * 需要提供旧密码进行验证，验证通过后才能设置新密码。
     * </p>
     *
     * @param changePasswordDTO 包含旧密码和新密码的 DTO
     * @return 修改结果
     */
    @PostMapping("/change-password")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 2.0, timeout = 500)
    public Result<String> changePassword(@RequestBody ChangePasswordDTO changePasswordDTO) {
        return userCenterService.changePassword(changePasswordDTO.getOldPassword(), changePasswordDTO.getNewPassword());
    }
}
