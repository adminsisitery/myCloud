package com.ff.mycloud.mq.config;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.constant.ESConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 交换机、队列和绑定声明（统一入口）
 * <p>所有队列均配置 DLX 死信交换机，配合 application.yml 中
 * default-requeue-rejected=false，消费者抛异常后消息进入 DLQ，
 * 由运维人员通过管理面板查看和处理。</p>
 *
 * @author 飞飞
 * @date 2026/07/10
 */
@Configuration
public class RabbitMQConfig {

    /** 死信参数模板，避免重复构造 */
    private static Map<String, Object> dlxArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", CommonConstants.DLQ.DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", CommonConstants.DLQ.DLX_KEY);
        return args;
    }

    // ==================== 死信基础设施 ====================

    @Bean
    public TopicExchange dlxExchange() {
        return ExchangeBuilder.topicExchange(CommonConstants.DLQ.DLX_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(CommonConstants.DLQ.DLX_QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange()).with(CommonConstants.DLQ.DLX_KEY);
    }

    // ==================== 用量通知 ====================

    @Bean
    public TopicExchange usageExchange() {
        return ExchangeBuilder.topicExchange(CommonConstants.USAGE.USAGE_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue usageQueue() {
        return QueueBuilder.durable(CommonConstants.USAGE.USAGE_QUEUE)
                .withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding usageBinding() {
        return BindingBuilder.bind(usageQueue())
                .to(usageExchange()).with(CommonConstants.USAGE.USAGE_KEY);
    }

    // ==================== ES 同步 ====================

    @Bean
    public TopicExchange esTopicExchange() {
        return ExchangeBuilder.topicExchange(ESConstants.EXCHANGE.TOPIC_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue esInsertQueue() {
        return QueueBuilder.durable(ESConstants.QUEUE.INSERT_QUEUE)
                .withArguments(dlxArgs()).build();
    }

    @Bean
    public Queue esDeleteQueue() {
        return QueueBuilder.durable(ESConstants.QUEUE.DELETE_QUEUE)
                .withArguments(dlxArgs()).build();
    }

    @Bean
    public Queue esUpdateQueue() {
        return QueueBuilder.durable(ESConstants.QUEUE.UPDATE_QUEUE)
                .withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding bindInsert() {
        return BindingBuilder.bind(esInsertQueue())
                .to(esTopicExchange()).with(ESConstants.KEY.INSERT_KEY);
    }

    @Bean
    public Binding bindDelete() {
        return BindingBuilder.bind(esDeleteQueue())
                .to(esTopicExchange()).with(ESConstants.KEY.DELETE_KEY);
    }

    @Bean
    public Binding bindUpdate() {
        return BindingBuilder.bind(esUpdateQueue())
                .to(esTopicExchange()).with(ESConstants.KEY.UPDATE_KEY);
    }

    // ==================== PG 向量同步 ====================

    @Bean
    public TopicExchange pgTopicExchange() {
        return ExchangeBuilder.topicExchange(CommonConstants.PG.TOPIC_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue pgInsertQueue() {
        return QueueBuilder.durable(CommonConstants.PG.INSERT_QUEUE)
                .withArguments(dlxArgs()).build();
    }

    @Bean
    public Queue pgDeleteQueue() {
        return QueueBuilder.durable(CommonConstants.PG.DELETE_QUEUE)
                .withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding bindPgInsert() {
        return BindingBuilder.bind(pgInsertQueue())
                .to(pgTopicExchange()).with(CommonConstants.PG.INSERT_KEY);
    }

    @Bean
    public Binding bindPgDelete() {
        return BindingBuilder.bind(pgDeleteQueue())
                .to(pgTopicExchange()).with(CommonConstants.PG.DELETE_KEY);
    }

    // ==================== 操作日志 ====================

    @Bean
    public TopicExchange logTopicExchange() {
        return ExchangeBuilder.topicExchange(CommonConstants.LogMQ.EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue logQueue() {
        return QueueBuilder.durable(CommonConstants.LogMQ.QUEUE)
                .withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding bindLog() {
        return BindingBuilder.bind(logQueue())
                .to(logTopicExchange()).with(CommonConstants.LogMQ.KEY);
    }

    // ==================== 文件夹异步打包 ====================

    @Bean
    public TopicExchange folderDownloadExchange() {
        return ExchangeBuilder.topicExchange(CommonConstants.FolderDownloadMQ.EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue folderDownloadQueue() {
        return QueueBuilder.durable(CommonConstants.FolderDownloadMQ.QUEUE)
                .withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding folderDownloadBinding() {
        return BindingBuilder.bind(folderDownloadQueue())
                .to(folderDownloadExchange()).with(CommonConstants.FolderDownloadMQ.KEY);
    }
}
