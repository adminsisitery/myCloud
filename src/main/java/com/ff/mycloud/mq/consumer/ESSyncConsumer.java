package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.common.constant.ESConstants;
import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.service.FileSearchService;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * ES 同步消费者
 * 消费 MQ 消息，最终一致地维护 ES 文件索引
 *
 * 幂等说明：以下三个操作均为天然幂等，无需额外去重
 *   INSERT → ES save() 内部 upsert，同 ID 写两次结果一致
 *   DELETE → ES deleteById() 删除不存在记录不会报错（no-op）
 *   UPDATE → renameById findById + save，改成同一名字结果一致
 */
@Component
public class ESSyncConsumer {

    private final FileSearchService fileSearchService;

    public ESSyncConsumer(FileSearchService fileSearchService) {
        this.fileSearchService = fileSearchService;
    }

    /**
     * 文件上传/秒传 → 写入 ES 索引
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
     * 文件删除 → 删除 ES 索引
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
     * 文件重命名 → 更新 ES 索引
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
