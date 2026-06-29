package com.ff.mycloud.task;

import com.ff.mycloud.enums.UploadTaskStatus;
import com.ff.mycloud.model.entity.UploadTask;
import com.ff.mycloud.redis.ChunkUploadRedis;
import com.ff.mycloud.redis.UploadProgressRedis;
import com.ff.mycloud.service.StorageServiceFactory;
import com.ff.mycloud.service.UpLoadProgressService;
import com.ff.mycloud.service.UserCenterMapperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * UploadTimeoutTask 单元测试
 * <p>测试每5分钟扫描Redis中超时上传任务的逻辑</p>
 */
class UploadTimeoutTaskTest {

    @Mock private UploadProgressRedis uploadProgressRedis;
    @Mock private UpLoadProgressService upLoadProgressService;
    @Mock private ChunkUploadRedis chunkUploadRedis;
    @Mock private StorageServiceFactory storageServiceFactory;
    @Mock private UserCenterMapperService userCenterMapperService;

    private UploadTimeoutTask task;

    private static final long NOW = System.currentTimeMillis();
    /** 超时阈值：30分钟 */
    private static final long TIMEOUT_MS = 30 * 60 * 1000;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        task = new UploadTimeoutTask(uploadProgressRedis, upLoadProgressService,
                chunkUploadRedis, storageServiceFactory, userCenterMapperService);
    }

    // ==================== 正常流程 ====================

    @Test
    @DisplayName("PENDING 任务超过30分钟 → 标记失败")
    void shouldMarkPendingTaskAsFailedWhenTimeout() {
        UploadTask uploadTask = buildTask("task-1", UploadTaskStatus.PENDING,
                NOW - TIMEOUT_MS - 1); // 刚超时1ms

        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of("task-1"));
        when(uploadProgressRedis.getTask("task-1")).thenReturn(uploadTask);

        task.scanTimeoutTasks();

        verify(upLoadProgressService).completeUploadTask(eq("task-1"),
                eq(false),
                argThat(msg -> msg.contains("超时") && msg.contains("PENDING")));
    }

    @Test
    @DisplayName("UPLOADING 任务超过30分钟 → 标记失败")
    void shouldMarkUploadingTaskAsFailedWhenTimeout() {
        UploadTask uploadTask = buildTask("task-2", UploadTaskStatus.UPLOADING,
                NOW - TIMEOUT_MS - 60000); // 超时1分钟

        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of("task-2"));
        when(uploadProgressRedis.getTask("task-2")).thenReturn(uploadTask);

        task.scanTimeoutTasks();

        verify(upLoadProgressService).completeUploadTask(eq("task-2"),
                eq(false),
                argThat(msg -> msg.contains("超时") && msg.contains("UPLOADING")));
    }

    @Test
    @DisplayName("未超时任务 → 不触发失败标记")
    void shouldNotMarkTaskAsFailedWhenNotTimeout() {
        // 仅创建5分钟
        UploadTask uploadTask = buildTask("task-3", UploadTaskStatus.UPLOADING,
                NOW - 5 * 60 * 1000);

        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of("task-3"));
        when(uploadProgressRedis.getTask("task-3")).thenReturn(uploadTask);

        task.scanTimeoutTasks();

        // 未超时，不应调用 completeUploadTask
        verify(upLoadProgressService, never()).completeUploadTask(anyString(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("多任务混合：仅超时任务被标记")
    void shouldOnlyFlagTimeoutTasksInMixedList() {
        UploadTask timeoutTask = buildTask("timeout-1", UploadTaskStatus.PENDING,
                NOW - TIMEOUT_MS - 10000);
        UploadTask freshTask = buildTask("fresh-1", UploadTaskStatus.UPLOADING,
                NOW - 60_000);

        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of("timeout-1", "fresh-1"));
        when(uploadProgressRedis.getTask("timeout-1")).thenReturn(timeoutTask);
        when(uploadProgressRedis.getTask("fresh-1")).thenReturn(freshTask);

        task.scanTimeoutTasks();

        verify(upLoadProgressService).completeUploadTask(eq("timeout-1"), anyBoolean(), anyString());
        verify(upLoadProgressService, never()).completeUploadTask(eq("fresh-1"), anyBoolean(), anyString());
    }

    // ==================== 终态跳过 ====================

    @Test
    @DisplayName("SUCCESS 终态任务 → 跳过（即使超时）")
    void shouldSkipSuccessTaskEvenIfTimeout() {
        UploadTask uploadTask = buildTask("task-4", UploadTaskStatus.SUCCESS,
                NOW - TIMEOUT_MS - 3600_000); // 远超30分钟

        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of("task-4"));
        when(uploadProgressRedis.getTask("task-4")).thenReturn(uploadTask);

        task.scanTimeoutTasks();

        verify(upLoadProgressService, never()).completeUploadTask(anyString(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("FAILED 终态任务 → 跳过")
    void shouldSkipAlreadyFailedTask() {
        UploadTask uploadTask = buildTask("task-5", UploadTaskStatus.FAILED,
                NOW - TIMEOUT_MS - 3600_000);

        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of("task-5"));
        when(uploadProgressRedis.getTask("task-5")).thenReturn(uploadTask);

        task.scanTimeoutTasks();

        verify(upLoadProgressService, never()).completeUploadTask(anyString(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("CANCELLED 终态任务 → 跳过")
    void shouldSkipCancelledTask() {
        UploadTask uploadTask = buildTask("task-6", UploadTaskStatus.CANCELLED,
                NOW - TIMEOUT_MS - 3600_000);

        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of("task-6"));
        when(uploadProgressRedis.getTask("task-6")).thenReturn(uploadTask);

        task.scanTimeoutTasks();

        verify(upLoadProgressService, never()).completeUploadTask(anyString(), anyBoolean(), anyString());
    }

    // ==================== 边界场景 ====================

    @Test
    @DisplayName("Redis 无任务 → 直接返回")
    void shouldReturnImmediatelyWhenNoTasks() {
        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of());

        task.scanTimeoutTasks();

        verify(uploadProgressRedis, never()).getTask(anyString());
        verify(upLoadProgressService, never()).completeUploadTask(anyString(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("任务已从 Redis 删除（getTask 返回 null）→ 跳过")
    void shouldSkipWhenTaskNotFoundInRedis() {
        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of("ghost-task"));
        when(uploadProgressRedis.getTask("ghost-task")).thenReturn(null);

        task.scanTimeoutTasks();

        verify(upLoadProgressService, never()).completeUploadTask(anyString(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("正好30分钟（不超时）→ 不触发")
    void shouldNotTriggerAtExactly30Minutes() {
        // NOW 是静态初始化时的值，到 scanTimeoutTasks() 执行时已有时间差
        // 用 NOW - TIMEOUT_MS + 30s 确保在 cutoff 之后，不会被判定为超时
        UploadTask uploadTask = buildTask("exact-30", UploadTaskStatus.PENDING,
                NOW - TIMEOUT_MS + 30_000);

        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of("exact-30"));
        when(uploadProgressRedis.getTask("exact-30")).thenReturn(uploadTask);

        task.scanTimeoutTasks();

        verify(upLoadProgressService, never()).completeUploadTask(anyString(), anyBoolean(), anyString());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建测试用 UploadTask（通过反射设值来模拟不同状态和时间）
     */
    private UploadTask buildTask(String id, UploadTaskStatus status, long createdAt) {
        UploadTask t = new UploadTask(id, "test-" + id + ".bin", 1024 * 1024 * 100L);

        // 使用 mock/spy 方式修改 final 字段的 createdAt
        // 因为 UploadTask.createdAt 是 final long，通过构造函数后无法直接修改。
        // 实际测试中 scanTimeoutTasks 读取的是从 Redis 反序列化回来的对象，
        // 反序列化时会正确填充 createdAt。这里用 spy 模拟。
        UploadTask spy = spy(t);
        doReturn(createdAt).when(spy).getCreatedAt();

        // 设置状态（直接设，因为 status 不是 final）
        spy.setStatus(status);

        // 对于终态，设 completed=true
        if (status == UploadTaskStatus.SUCCESS
                || status == UploadTaskStatus.FAILED
                || status == UploadTaskStatus.CANCELLED) {
            spy.setCompleted(true);
        }

        return spy;
    }
}
