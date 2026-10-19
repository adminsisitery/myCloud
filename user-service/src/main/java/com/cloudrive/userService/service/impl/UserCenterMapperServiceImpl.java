package com.cloudrive.userService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudrive.userService.domain.po.UserCenter;
import com.cloudrive.userService.mapper.UserCenterMapper;
import com.cloudrive.userService.service.UserCenterMapperService;
import org.springframework.stereotype.Service;

/**
 * 用户中心 Mapper 服务实现类
 * <p>基于MyBatis-Plus实现用户中心表的数据操作，包括查询、空间释放和预留等功能。</p>
 *
 * @author 飞飞
 * @date 2026/3/8
 */
@Service
public class UserCenterMapperServiceImpl extends ServiceImpl<UserCenterMapper, UserCenter> implements UserCenterMapperService {

    /**
     * 根据用户ID获取用户中心信息
     *
     * @param currentUserId 用户ID
     * @return 用户中心实体，不存在则返回null
     */
    @Override
    public UserCenter getByUserId(Long currentUserId) {
        return lambdaQuery()
                .eq(UserCenter::getUserId, currentUserId)
                .one();
    }

    /**
     * 释放用户已用空间（减少已用空间字节数）
     *
     * @param currentUserId 用户ID
     * @param size          要释放的空间大小（字节）
     */
    @Override
    public void releaseSpace(Long currentUserId, long size) {
        baseMapper.releaseSpace(currentUserId, size);
    }

    /**
     * 预留用户空间（检查可用空间并扣减）
     * <p>使用数据库行级锁保证并发安全，如果可用空间不足则扣减失败。</p>
     *
     * @param userId   用户ID
     * @param fileSize 需要预留的文件大小（字节）
     * @return true表示空间预留成功
     */
    @Override
    public boolean reserveSpace(long userId, long fileSize) {
        return baseMapper.reserveSpace(userId, fileSize) > 0;
    }
}
