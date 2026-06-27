package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.model.entity.OperationLog;
import com.ff.mycloud.repositorys.OperationLogMapper;
import com.ff.mycloud.service.OperationLogMapperService;
import org.springframework.stereotype.Service;

/**
 * 操作日志数据访问服务实现类
 * <p>
 * 基于MyBatis-Plus ServiceImpl实现，提供OperationLog表的基础CRUD操作。
 * 操作日志数据由MQ消费者异步写入，通过msgId实现幂等去重。
 * </p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Service
public class OperationLogMapperServiceImpl
        extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogMapperService {
}
