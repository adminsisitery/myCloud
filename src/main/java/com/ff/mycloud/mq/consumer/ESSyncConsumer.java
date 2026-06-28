package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.common.constant.ESConstants;
import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.service.FileSearchService;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 文件索引同步消费者
 *
 * 消费 RabbitMQ 消息，异步维护 ES 文件索引，实现最终一致性。
 * 监听三个队列分别处理文件的新增、删除、重命名索引同步。
 *
 * 幂等说明：以下三个操作均为天然幂等，无需额外去重
 *   INSERT → ES save() 内部 upsert，同 ID 写两次结果一致
 *   DELETE → ES deleteById() 删除不存在记录不会报错（no-op）
 *   UPDATE → renameById findById + save，改成同一名字结果一致
 *
 * @author 飞飞
 * @date 2026/3/14
 */
@Component
@ConditionalOnProperty(prefix = "file", name = "search", havingValue = "ES")
public class ESSyncConsumer {

    private final FileSearchService fileSearchService;

    /**
     * 构造方法注入ES文件搜索服务
     *
     * @param fileSearchService 文件搜索服务，封装了ES索引的增删改操作
     */
    public ESSyncConsumer(FileSearchService fileSearchService) {
        this.fileSearchService = fileSearchService;
    }

    /**
     * 文件上传/秒传 → 写入ES索引
     *
     * 监听INSERT_QUEUE队列，消费文件新增消息并写入Elasticsearch索引。
     * ES save()方法内部使用upsert语义，天然幂等。
     *
     * @param message 文件文档对象，包含文件ID、文件名、用户ID等索引信息
     */
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(ESConstants.QUEUE.INSERT_QUEUE),
                    exchange = @Exchange(ESConstants.EXCHANGE.TOPIC_EXCHANGE),
                    key = ESConstants.KEY.INSERT_KEY
            )
    )
    public void esInsert(FileDocument message) {
        fileSearchService.save(message);
    }

    /**
     * 文件删除 → 从ES索引中删除
     *
     * 监听DELETE_QUEUE队列，消费文件删除消息并移除Elasticsearch中的对应索引。
     * deleteById()删除不存在的记录不会报错（no-op），天然幂等。
     *
     * @param message 文件文档对象，包含待删除的文件ID
     */
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(ESConstants.QUEUE.DELETE_QUEUE),
                    exchange = @Exchange(ESConstants.EXCHANGE.TOPIC_EXCHANGE),
                    key = ESConstants.KEY.DELETE_KEY
            )
    )
    public void esDelete(FileDocument message) {
        fileSearchService.del(message.getId());
    }

    /**
     * 文件重命名 → 更新ES索引中的文件名
     *
     * 监听UPDATE_QUEUE队列，消费文件重命名消息并更新Elasticsearch索引中的文件名。
     * renameById内部先findById再save，改成同一名字结果一致，天然幂等。
     *
     * @param message 文件文档对象，包含文件ID和新的文件名
     */
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(ESConstants.QUEUE.UPDATE_QUEUE),
                    exchange = @Exchange(ESConstants.EXCHANGE.TOPIC_EXCHANGE),
                    key = ESConstants.KEY.UPDATE_KEY
            )
    )
    public void esUpdate(FileDocument message) {
        fileSearchService.renameById(message.getId(), message.getFileName());
    }

}
