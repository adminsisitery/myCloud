package com.cloudrive.aiService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudrive.aiService.model.entity.FileInfo;
import com.cloudrive.aiService.repositorys.FileInfoMapper;
import com.cloudrive.aiService.service.FileInfoMapperService;
import org.springframework.stereotype.Service;

/**
 * 文件信息查询服务实现
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class FileInfoMapperServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo>
        implements FileInfoMapperService {
}
