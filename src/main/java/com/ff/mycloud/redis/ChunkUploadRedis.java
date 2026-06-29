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

    /**
     * 删除分片上传任务（含主任务和所有分片 key）
     */
    public void deleteTask(String uploadId) {
        // 删除主任务
        redisClient.getKeys().delete(KEY_PREFIX + uploadId);
        // 删除所有分片 key
        deletePartKeys(uploadId);
    }

    /**
     * 判断分片是否已上传完成
     */
    public boolean isPartUploaded(String uploadId, int partNumber) {
        return redisClient.getBucket(KEY_PREFIX + uploadId + KEY_PARTS + partNumber).isExists();
    }

    /**
     * 记录分片完成并保存 ETag
     *
     * @param uploadId   自定义上传ID
     * @param partNumber 分片编号
     * @param eTag       分片ETag（用于最终合并）
     */
    public void savePartETag(String uploadId, int partNumber, String eTag) {
        RBucket<String> bucket = redisClient.getBucket(KEY_PREFIX + uploadId + KEY_PARTS + partNumber);
        bucket.set(eTag);
    }

    /**
     * 获取所有分片的 ETag 列表（按 partNumber 升序）
     *
     * @param uploadId 自定义上传ID
     * @return 有序的 PartETag 列表，用于 OSS CompleteMultipartUpload
     */
    public List<PartETag> getPartETags(String uploadId) {
        String pattern = KEY_PREFIX + uploadId + KEY_PARTS + "*";
        List<PartETag> result = new ArrayList<>();

        for (String key : redisClient.getKeys().getKeysByPattern(pattern)) {
            // 读取该分片的 ETag 字符串
            String eTag = (String) redisClient.getBucket(key).get();
            if (eTag == null) continue;

            // 从 key 中提取 partNumber：chunk:upload:{id}:parts{num}
            int partNumber = Integer.parseInt(key.substring(key.lastIndexOf(KEY_PARTS) + KEY_PARTS.length()));

            result.add(new PartETag(partNumber, eTag));
        }

        // 按 partNumber 升序排列
        result.sort(Comparator.comparingInt(PartETag::getPartNumber));
        return result;
    }

    /**
     * 删除所有分片 key（合并完成后清理）
     *
     * @param uploadId 自定义上传ID
     */
    public void deletePartKeys(String uploadId) {
        String pattern = KEY_PREFIX + uploadId + KEY_PARTS + "*";
        for (String key : redisClient.getKeys().getKeysByPattern(pattern)) {
            redisClient.getKeys().delete(key);
        }
    }
}
