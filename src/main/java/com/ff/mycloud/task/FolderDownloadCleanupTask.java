package com.ff.mycloud.task;

import com.ff.mycloud.service.FolderDownloadTaskService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FolderDownloadCleanupTask {
    private final FolderDownloadTaskService service;

    public FolderDownloadCleanupTask(FolderDownloadTaskService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 */10 * * * ?")
    @SchedulerLock(name = "folderDownloadCleanup", lockAtMostFor = "PT9M")
    public void cleanup() {
        service.cleanupExpired();
    }
}
