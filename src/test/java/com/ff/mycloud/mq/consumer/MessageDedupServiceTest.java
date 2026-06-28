package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.common.constant.CommonConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MessageDedupService 单元测试
 * <p>测试基于Redis SETNX的MQ消息去重逻辑</p>
 */
class MessageDedupServiceTest {

    @Mock private RedissonClient redissonClient;
    @Mock private RBucket<String> bucket;

    private MessageDedupService service;

    private static final String TEST_MSG_ID = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MessageDedupService(redissonClient);
    }

    // ==================== 正常流程 ====================

    @Test
    @DisplayName("首次消费：SETNX返回true → 应消费")
    void shouldReturnTrueForFirstConsumption() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(true);

        boolean result = service.tryConsume(TEST_MSG_ID);

        assertTrue(result, "首次消费应返回true");
        verify(bucket).setIfAbsent(eq("1"),
                eq(Duration.ofSeconds(CommonConstants.Dedup.TTL_SECONDS)));
    }

    @Test
    @DisplayName("重复消费：SETNX返回false → 应跳过")
    void shouldReturnFalseForDuplicateConsumption() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(false);

        boolean result = service.tryConsume(TEST_MSG_ID);

        assertFalse(result, "重复消费应返回false");
    }

    @Test
    @DisplayName("Redis key 格式应为 msg:consumed:{msgId}")
    void shouldUseCorrectRedisKeyFormat() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(true);

        service.tryConsume(TEST_MSG_ID);

        String expectedKey = CommonConstants.Dedup.KEY_PREFIX + TEST_MSG_ID;
        verify(redissonClient).getBucket(expectedKey);
    }

    @Test
    @DisplayName("SETNX TTL 应为24小时")
    void shouldUse24HourTTL() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);

        service.tryConsume(TEST_MSG_ID);

        verify(bucket).setIfAbsent(eq("1"),
                eq(Duration.ofSeconds(24 * 60 * 60)));
    }

    // ==================== 边界场景 ====================

    @Test
    @DisplayName("msgId 为 null → 兼容放行")
    void shouldAllowNullMsgIdForBackwardCompatibility() {
        // null msgId 不走 Redis，直接放行
        boolean result = service.tryConsume(null);

        assertTrue(result, "null msgId 应放行（兼容旧消息）");
        verify(redissonClient, never()).getBucket(anyString());
    }

    @Test
    @DisplayName("msgId 为空字符串 → 兼容放行")
    void shouldAllowEmptyMsgIdForBackwardCompatibility() {
        boolean result = service.tryConsume("");

        assertTrue(result, "空字符串 msgId 应放行");
        verify(redissonClient, never()).getBucket(anyString());
    }

    @Test
    @DisplayName("msgId 为空白字符串 → 不走兼容，正常进Redis")
    void shouldProcessBlankMsgIdThroughRedis() {
        // "   " 不是 null 也不是 isEmpty()，会正常进 Redis
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(true);

        boolean result = service.tryConsume("   ");

        // 空白字符串不触发 null/empty 兼容，会走 SETNX
        verify(redissonClient).getBucket(anyString());
        verify(bucket).setIfAbsent(eq("1"), any(Duration.class));
        assertTrue(result);
    }

    @Test
    @DisplayName("多次相同 msgId 第一次成功后续全失败")
    void shouldBlockAllDuplicatesAfterFirstSuccess() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.setIfAbsent(eq("1"), any(Duration.class)))
                .thenReturn(true)   // 第1次：成功
                .thenReturn(false)  // 第2次：重复
                .thenReturn(false); // 第3次：重复

        assertTrue(service.tryConsume(TEST_MSG_ID), "第1次应成功");
        assertFalse(service.tryConsume(TEST_MSG_ID), "第2次应失败（重复）");
        assertFalse(service.tryConsume(TEST_MSG_ID), "第3次应失败（重复）");

        // SETNX 应被调用3次（每次都尝试）
        verify(bucket, times(3)).setIfAbsent(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("不同 msgId 互不影响")
    void shouldTreatDifferentMsgIdsIndependently() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(true);

        assertTrue(service.tryConsume("msg-aaa"));
        assertTrue(service.tryConsume("msg-bbb"));
        assertTrue(service.tryConsume("msg-ccc"));

        // 三个不同的 msgId，应使用三个不同的 key
        verify(redissonClient).getBucket(CommonConstants.Dedup.KEY_PREFIX + "msg-aaa");
        verify(redissonClient).getBucket(CommonConstants.Dedup.KEY_PREFIX + "msg-bbb");
        verify(redissonClient).getBucket(CommonConstants.Dedup.KEY_PREFIX + "msg-ccc");
    }
}
