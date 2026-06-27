package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.model.entity.OperationLog;

/**
 * 操作日志数据访问服务接口
 * <p>
 * 继承MyBatis-Plus IService，提供OperationLog的CRUD操作。
 * 操作日志通过MQ异步消费后写入数据库，记录用户的文件操作行为。
 * </p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
public interface OperationLogMapperService extends IService<OperationLog> {
}
