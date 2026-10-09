package com.ff.mycloud.service;

import com.ff.mycloud.enums.UploadTaskStatus;
import com.ff.mycloud.model.entity.UploadTask;
import com.ff.mycloud.redis.UploadProgressRedis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpLoadProgressServiceTest {

    private static final String TASK_ID = "task-1";

    private UploadTask task;
    private UpLoadProgressService service;

    @BeforeEach
    void setUp() {
        UploadProgressRedis redis = mock(UploadProgressRedis.class);
        task = new UploadTask(TASK_ID, "demo.bin", 100);
        service = new UpLoadProgressService(redis);

        when(redis.getTask(TASK_ID)).thenAnswer(invocation -> task);
        when(redis.updateTask(eq(TASK_ID), any())).thenAnswer(invocation -> {
            Function<UploadTask, Object> updater = invocation.getArgument(1);
            return updater.apply(task);
        });
    }

    @Test
    void shouldMoveFromPendingToUploadingToSuccess() {
        service.markUploading(TASK_ID);
        service.updateBytesTransferred(TASK_ID, 60);
        service.completeUploadTask(TASK_ID, true, "ok");

        assertEquals(UploadTaskStatus.SUCCESS, task.getStatus());
        assertEquals(100, task.getBytesTransferred());
        assertEquals(100, task.getProgress());
        assertTrue(task.isCompleted());
        assertTrue(task.isSuccess());
    }

    @Test
    void failedTaskShouldKeepActualProgress() {
        service.markUploading(TASK_ID);
        service.updateBytesTransferred(TASK_ID, 40);
        service.completeUploadTask(TASK_ID, false, "failed");

        assertEquals(UploadTaskStatus.FAILED, task.getStatus());
        assertEquals(40, task.getBytesTransferred());
        assertEquals(40, task.getProgress());
        assertTrue(task.isCompleted());
        assertFalse(task.isSuccess());
    }

    @Test
    void cancelledTaskShouldNotBeOverwritten() {
        service.cancelUploadTask(TASK_ID, "cancelled");
        service.completeUploadTask(TASK_ID, true, "ok");
        service.completeUploadTask(TASK_ID, false, "failed");

        assertEquals(UploadTaskStatus.CANCELLED, task.getStatus());
        assertEquals("cancelled", task.getMessage());
        assertFalse(task.isSuccess());
    }

    @Test
    void spaceReleaseShouldOnlyBeClaimedOnce() {
        assertTrue(service.tryMarkSpaceReleased(TASK_ID));
        assertFalse(service.tryMarkSpaceReleased(TASK_ID));
    }

    @Test
    void reservationMarkerShouldKeepCancelledTaskScannable() {
        // 取消把任务置成终态。没有预占标记时，终态不该被超时扫描捡起（原有行为）
        service.cancelUploadTask(TASK_ID, "cancelled");
        assertFalse(task.holdsUnsettledReservation());

        // 一旦标记了「预占未结清」，即便已是终态也必须能被扫描捡起来归还 ——
        // 这是「用户取消 + 异步线程从未执行」导致空间永久泄漏的那个盲区
        service.markSpaceReserved(TASK_ID);
        assertTrue(task.isSpaceReserved());
        assertTrue(task.holdsUnsettledReservation());

        // 结清之后不再处理
        service.clearSpaceReserved(TASK_ID);
        assertFalse(task.isSpaceReserved());
        assertFalse(task.holdsUnsettledReservation());
    }

    @Test
    void successfulTaskShouldNeverBeTreatedAsRefundable() {
        service.markSpaceReserved(TASK_ID);
        service.completeUploadTask(TASK_ID, true, "ok");

        // 文件已落库，预占转为真实占用：即使标记残留也不得视为可退还
        assertTrue(task.isSpaceReserved());
        assertFalse(task.holdsUnsettledReservation());
    }
}
