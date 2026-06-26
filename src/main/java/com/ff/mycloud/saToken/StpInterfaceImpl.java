package com.ff.mycloud.saToken;

import cn.dev33.satoken.stp.StpInterface;
import com.ff.mycloud.service.UserMapperService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StpInterfaceImpl implements StpInterface {


    private final UserMapperService userMapperService;

    public StpInterfaceImpl(UserMapperService userMapperService) {
        this.userMapperService = userMapperService;
    }

    @Override
    public List<String> getPermissionList(Object o, String s) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object o, String s) {
        long id = Long.parseLong(o.toString());
        List<String> roleById = userMapperService.getRoleById(id);
        if (roleById == null) {
            return List.of("user");
        }
        return roleById;
    }
}
