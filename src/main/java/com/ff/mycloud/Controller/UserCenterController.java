package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.mapper.UserCenterMapper;
import com.ff.mycloud.model.entity.ChangePasswordDTO;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.service.UserCenterService;
import com.ff.mycloud.service.UserMapperService;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * @description: TODO 
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

    public UserCenterController(UserMapperService userMapperService, UserCenterMapper userCenterMapper, UserCenterService userCenterService) {
        this.userMapperService = userMapperService;
        this.userCenterMapper = userCenterMapper;
        this.userCenterService = userCenterService;
    }

    @GetMapping("/info")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 10.0, timeout = 500)
    public Result<UserCenter> info() {
        UserCenter userCenter = userCenterService.getUserCenterInfo();
        return Result.success(userCenter);
    }
    @PostMapping("/change-password")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 2.0, timeout = 500)
    public Result<String> changePassword(@RequestBody ChangePasswordDTO changePasswordDTO) {
        return userCenterService.changePassword(changePasswordDTO.getOldPassword(), changePasswordDTO.getNewPassword());
    }
}
