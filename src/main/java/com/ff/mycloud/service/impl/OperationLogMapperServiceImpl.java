package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.model.entity.OperationLog;
import com.ff.mycloud.repositorys.OperationLogMapper;
import com.ff.mycloud.service.OperationLogMapperService;
import org.springframework.stereotype.Service;

@Service
public class OperationLogMapperServiceImpl
        extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogMapperService {
}
