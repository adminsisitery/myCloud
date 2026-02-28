package com.ff.mycloud.service;

import com.ff.mycloud.service.impl.OssStorageServiceImpl;
import org.springframework.stereotype.Component;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/2/22 19:49
 * @version 1.0
 */

@Component
public class StorageServiceFactory {
    private final OssStorageServiceImpl ossStorageService;

    public StorageServiceFactory(OssStorageServiceImpl ossStorageService) {
        this.ossStorageService = ossStorageService;
    }
    //这是方便以后改oss 在这直接改实现类就行
    public StorageService getStorageService() {
        return ossStorageService;
    }
}
