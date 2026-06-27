package com.ff.mycloud.service;

import com.ff.mycloud.service.impl.OssStorageServiceImpl;
import org.springframework.stereotype.Component;

/**
 * 存储服务工厂类
 * <p>
 * 通过工厂模式封装存储服务的获取逻辑，统一对外提供StorageService实例。
 * 当需要切换存储后端（如从阿里云OSS切换为其他云存储或本地存储）时，
 * 只需在此工厂中修改返回的具体实现类即可，业务代码无需变动。
 * </p>
 *
 * @author 飞飞
 * @date 2026/2/22 19:49
 * @version 1.0
 */
@Component
public class StorageServiceFactory {

    private final OssStorageServiceImpl ossStorageService;

    /**
     * 构造函数，注入OSS存储服务实现
     *
     * @param ossStorageService OSS存储服务实例
     */
    public StorageServiceFactory(OssStorageServiceImpl ossStorageService) {
        this.ossStorageService = ossStorageService;
    }

    /**
     * 获取当前使用的存储服务实例
     * <p>
     * 当前返回OSS实现，方便以后修改存储方案时只需更改此处返回的实现类即可。
     * </p>
     *
     * @return 存储服务实例
     */
    public StorageService getStorageService() {
        return ossStorageService;
    }
}
