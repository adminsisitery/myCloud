package com.cloudrive.fileAsyncService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudrive.fileAsyncService.model.entity.OperationLog;
import com.cloudrive.fileAsyncService.repositorys.OperationLogMapper;
import com.cloudrive.fileAsyncService.service.OperationLogMapperService;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现
 */
@Service
public class OperationLogMapperServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogMapperService {
}
