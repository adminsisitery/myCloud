package com.ff.mycloud.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ShedLock 分布式锁配置类
 * <p>
 * 基于 Redis 实现分布式定时任务锁，确保同一时刻只有一个节点执行定时任务。
 * 默认锁持有时间 30 秒（PT30S），超过该时间锁会自动释放。
 * </p>
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S") // 默认锁持有时间 30 秒，防止死锁
public class ShedLockConfig {

    /**
     * 创建基于 Redis 的分布式锁提供者
     * <p>
     * 使用 Redis 作为锁存储后端，实现跨节点的定时任务互斥执行。
     * </p>
     *
     * @param connectionFactory Redis 连接工厂
     * @return RedisLockProvider 实例
     */
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory);
    }
}