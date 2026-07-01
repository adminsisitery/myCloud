package com.ff.mycloud.task;

import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.repositorys.FileInfoMapper;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.StorageService;
import com.ff.mycloud.service.StorageServiceFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收站过期文件清理定时任务
 * <p>每天凌晨 3:00 扫描 isDeleted=true 且 deletedAt 超过 30 天的文件，物理删除 OSS 和数据库记录。</p>
 *
 * @author 飞飞
 * @date 2026/07/01
 */
@Component
public class DeleteFileOverTime {

    private final FileInfoMapper fileInfoMapper;
    private final FileInfoMapperService fileInfoMapperService;
    private final StorageServiceFactory storageServiceFactory;

    public DeleteFileOverTime(FileInfoMapper fileInfoMapper,
                               FileInfoMapperService fileInfoMapperService,
                               StorageServiceFactory storageServiceFactory) {
        this.fileInfoMapper = fileInfoMapper;
        this.fileInfoMapperService = fileInfoMapperService;
        this.storageServiceFactory = storageServiceFactory;
    }

    /**
     * 每天凌晨 3:00 清理过期回收站文件
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "trashCleanup",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT30M")
    public void deleteTimeoutScan() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Long> expiredIds = fileInfoMapper.findOverTimeFile(thirtyDaysAgo);

        if (expiredIds.isEmpty()) {
            return;
        }

        log.info("回收站定时清理开始：共{}个过期文件", expiredIds.size());

        for (Long id : expiredIds) {
            try {
                FileInfo file = fileInfoMapperService.getById(id);
                if (file == null) {
                    continue;
                }

                if (!Boolean.TRUE.equals(file.getIsFolder())) {
                    long referenceCount = fileInfoMapperService.countFiles(file.getSha256Hash());
                    if (referenceCount <= 1) {
                        StorageService storageService = storageServiceFactory.getStorageService();
                        storageService.deleteFile(file.getPath());
                    }
                }

                fileInfoMapperService.removeById(id);
            } catch (Exception e) {
                log.error("定时清理文件失败：id={}", id, e);
            }
        }

        log.info("回收站定时清理完成：共处理{}条", expiredIds.size());
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeleteFileOverTime.class);
}
