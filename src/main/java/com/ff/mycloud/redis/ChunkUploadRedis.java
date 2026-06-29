package com.ff.mycloud.redis;

import com.aliyun.oss.model.PartETag;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.ChunkTask;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ChunkUploadRedis {

    private static final String KEY_PREFIX = CommonConstants.ChunkUpload.KEY_PREFIX;
    private static final String KEY_PARTS = CommonConstants.ChunkUpload.KEY_PARTS;

    /** 分片任务 TTL：7天 */
    private static final long TASK_TTL_DAYS = 7;

    private final RedissonClient redisClient;

    public ChunkUploadRedis(RedissonClient redisClient) {
        this.redisClient = redisClient;
    }

    /**
     * 保存分片上传任务到Redis
     */
    public void saveTask(ChunkTask chunkTask) {
        RBucket<ChunkTask> bucket = redisClient.getBucket(KEY_PREFIX + chunkTask.getUploadId());
        bucket.set(chunkTask, Duration.ofDays(TASK_TTL_DAYS));
    }

    /**
     * 获取分片上传任务
     */
    public ChunkTask getTask(String uploadId) {
        RBucket<ChunkTask> bucket = redisClient.getBucket(KEY_PREFIX + uploadId);
        return bucket.get();
    }

    // ==================== 分片进度位图 ====================

    /**
     * 判断分片是否已上传
     */
    public boolean isPartUploaded(String uploadId, int partNumber) {
        return redisClient.getBitSet(KEY_PREFIX + uploadId + ":progress").get(partNumber);
    }

    /**
     * 标记分片已完成
     */
    public void markPartDone(String uploadId, int partNumber) {
        redisClient.getBitSet(KEY_PREFIX + uploadId + ":progress").set(partNumber, true);
    }

    /**
     * 获取所有缺失的分片号（0 位）
     */
    public List<Integer> getMissingParts(String uploadId, int totalParts) {
        List<Integer> missing = new ArrayList<>();
        var bitmap = redisClient.getBitSet(KEY_PREFIX + uploadId + ":progress");
        for (int i = 1; i <= totalParts; i++) {
            if (!bitmap.get(i)) {
                missing.add(i);
            }
        }
        return missing;
    }

    /**
     * 保存分片 ETag
     */
    public void savePartETag(String uploadId, int partNumber, String eTag) {
        RBucket<String> bucket = redisClient.getBucket(KEY_PREFIX + uploadId + KEY_PARTS + partNumber);
        bucket.set(eTag);
    }

    /**
     * 获取所有分片的 ETag 列表（按 partNumber 升序）
     */
    public List<PartETag> getPartETags(String uploadId) {
        String pattern = KEY_PREFIX + uploadId + KEY_PARTS + "*";
        List<PartETag> result = new ArrayList<>();

        for (String key : redisClient.getKeys().getKeysByPattern(pattern)) {
            String eTag = (String) redisClient.getBucket(key).get();
            if (eTag == null) continue;

            int partNumber = Integer.parseInt(key.substring(key.lastIndexOf(KEY_PARTS) + KEY_PARTS.length()));
            result.add(new PartETag(partNumber, eTag));
        }

        result.sort(Comparator.comparingInt(PartETag::getPartNumber));
        return result;
    }

    /**
     * 删除任务的所有 Redis key（主任务 + 位图 + 分片 ETag）
     */
    public void deleteTask(String uploadId) {
        // 主任务
        redisClient.getKeys().delete(KEY_PREFIX + uploadId);
        // 位图
        redisClient.getKeys().delete(KEY_PREFIX + uploadId + ":progress");
        // 分片 ETag
        deletePartKeys(uploadId);
    }

    private void deletePartKeys(String uploadId) {
        String pattern = KEY_PREFIX + uploadId + KEY_PARTS + "*";
        for (String key : redisClient.getKeys().getKeysByPattern(pattern)) {
            redisClient.getKeys().delete(key);
        }
    }

    public List<Integer> getCompleteParts(String uploadId, Integer totalParts) {
        List<Integer> complete = new ArrayList<>();
        var bitmap = redisClient.getBitSet(KEY_PREFIX + uploadId + ":progress");
        for (int i = 1; i <= totalParts; i++) {
            if (bitmap.get(i)) {
                complete.add(i);
            }
        }
        return complete;
    }
}
