package com.cloudrive.fileTaskService.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudrive.fileTaskService.common.TaskConstants;
import com.cloudrive.fileTaskService.model.entity.ShareRecord;
import com.cloudrive.fileTaskService.repositorys.ShareRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShareExpireTask 单元测试：过期分享标记。
 * <p>
 * 这个任务只有一条 SQL，但它是「分享到点必须失效」的最后一道闸：
 * 漏标 = 过期链接还能访问 = 内容泄露。所以至少要锁住三件事——
 * 抢不到锁不重复执行、SQL 确实把 is_expired 置为 true、异常不把定时线程搞挂。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ShareExpireTaskTest {

    /**
     * LambdaUpdateWrapper 的 set(ShareRecord::getIsExpired, ...) 要靠 MyBatis-Plus 的 TableInfo
     * 把方法引用解析成列名。纯单测没有 Spring 上下文，得手动初始化一次，
     * 否则构造 wrapper 时会抛「can not find lambda cache」，SQL 根本发不出去。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ShareRecord.class);
    }

    @Mock RedissonClient redissonClient;
    @Mock RLock lock;
    @Mock ShareRecordMapper shareRecordMapper;

    @InjectMocks ShareExpireTask task;

    @Test
    @DisplayName("抢不到分布式锁 → 跳过本轮，不发 SQL")
    void markExpiredShares_未抢到锁时跳过() {
        when(redissonClient.getLock(TaskConstants.LOCK_SHARE_EXPIRE_MARK)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        task.markExpiredShares();

        verify(shareRecordMapper, never()).update(any(), any());
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("正常执行 → 把 is_expired 置 true 并刷新 updated_at，然后释放锁")
    void markExpiredShares_标记过期并释放锁() {
        when(redissonClient.getLock(TaskConstants.LOCK_SHARE_EXPIRE_MARK)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        task.markExpiredShares();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ShareRecord>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(shareRecordMapper).update(isNull(), captor.capture());

        String setClause = captor.getValue().getSqlSet();
        assertThat(setClause).contains("is_expired").contains("updated_at");

        // 条件是「过期时间早于现在 且 尚未标记」，只校验列名，不绑死拼接顺序
        String whereClause = captor.getValue().getSqlSegment();
        assertThat(whereClause).contains("expire_time").contains("is_expired");

        verify(lock).unlock();
    }

    @Test
    @DisplayName("SQL 执行异常 → 被吞掉，锁仍然释放（不能让定时线程挂掉）")
    void markExpiredShares_异常时仍释放锁() {
        when(redissonClient.getLock(TaskConstants.LOCK_SHARE_EXPIRE_MARK)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(shareRecordMapper.update(isNull(), any())).thenThrow(new RuntimeException("db down"));

        task.markExpiredShares();

        verify(lock).unlock();
    }
}
