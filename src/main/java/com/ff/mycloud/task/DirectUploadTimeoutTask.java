package com.ff.mycloud.task;

import com.ff.mycloud.service.DirectUploadService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DirectUploadTimeoutTask {
    private final DirectUploadService service;

    public DirectUploadTimeoutTask(DirectUploadService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    @SchedulerLock(name = "directUploadTimeoutScan", lockAtMostFor = "PT4M")
    public void cleanupExpiredTasks() {
        service.cleanupExpired();
    }
}
