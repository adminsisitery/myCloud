package com.cloudrive.pan_common.mq;

import com.cloudrive.pan_common.constant.MqNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * RabbitMQ 队列拓扑统一声明。
 * <p>
 * 只有配置了 {@code pan.rabbit.topology-enabled=true} 的服务（file-async-service）才会创建
 * 交换机/队列/绑定；其他服务（含生产者）不声明，只发消息。
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "pan.rabbit", name = "topology-enabled", havingValue = "true")
public class RabbitMqTopologyConfig {

    private static Map<String, Object> dlxArgs() {
        return Map.of(
                "x-dead-letter-exchange", MqNames.Dlq.EXCHANGE,
                "x-dead-letter-routing-key", MqNames.Dlq.KEY
        );
    }

    // ==================== 死信 ====================

    @Bean
    public TopicExchange dlxExchange() {
        return ExchangeBuilder.topicExchange(MqNames.Dlq.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(MqNames.Dlq.QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(MqNames.Dlq.KEY);
    }

    // ==================== ES 索引同步 ====================

    @Bean
    public TopicExchange esTopicExchange() {
        return ExchangeBuilder.topicExchange(MqNames.Es.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue esInsertQueue() {
        // DLX 由 Broker 的 policy 配置，这里不能追加参数
        return QueueBuilder.durable(MqNames.Es.INSERT_QUEUE).build();
    }

    @Bean
    public Queue esDeleteQueue() {
        return QueueBuilder.durable(MqNames.Es.DELETE_QUEUE).build();
    }

    @Bean
    public Queue esUpdateQueue() {
        return QueueBuilder.durable(MqNames.Es.UPDATE_QUEUE).build();
    }

    @Bean
    public Binding bindEsInsert() {
        return BindingBuilder.bind(esInsertQueue()).to(esTopicExchange()).with(MqNames.Es.INSERT_KEY);
    }

    @Bean
    public Binding bindEsDelete() {
        return BindingBuilder.bind(esDeleteQueue()).to(esTopicExchange()).with(MqNames.Es.DELETE_KEY);
    }

    @Bean
    public Binding bindEsUpdate() {
        return BindingBuilder.bind(esUpdateQueue()).to(esTopicExchange()).with(MqNames.Es.UPDATE_KEY);
    }

    // ==================== PG 向量同步 ====================

    @Bean
    public DirectExchange pgTopicExchange() {
        return ExchangeBuilder.directExchange(MqNames.Pg.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue pgInsertQueue() {
        return QueueBuilder.durable(MqNames.Pg.INSERT_QUEUE).build();
    }

    @Bean
    public Queue pgDeleteQueue() {
        return QueueBuilder.durable(MqNames.Pg.DELETE_QUEUE).build();
    }

    @Bean
    public Binding bindPgInsert() {
        return BindingBuilder.bind(pgInsertQueue()).to(pgTopicExchange()).with(MqNames.Pg.INSERT_KEY);
    }

    @Bean
    public Binding bindPgDelete() {
        return BindingBuilder.bind(pgDeleteQueue()).to(pgTopicExchange()).with(MqNames.Pg.DELETE_KEY);
    }

    // ==================== 操作日志 ====================

    @Bean
    public DirectExchange logTopicExchange() {
        return ExchangeBuilder.directExchange(MqNames.Log.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue logQueue() {
        // 重试 3 次仍失败时转死信，否则 default-requeue-rejected=false 会让消息直接消失
        // 注意：队列已存在且参数不同时启动会 PRECONDITION_FAILED，升级需先删旧队列或改用 Broker policy
        return QueueBuilder.durable(MqNames.Log.QUEUE).withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding bindLog() {
        return BindingBuilder.bind(logQueue()).to(logTopicExchange()).with(MqNames.Log.KEY);
    }

    // ==================== 用量通知 ====================

    @Bean
    public TopicExchange usageExchange() {
        return ExchangeBuilder.topicExchange(MqNames.Usage.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue usageQueue() {
        return QueueBuilder.durable(MqNames.Usage.QUEUE).withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding bindUsage() {
        return BindingBuilder.bind(usageQueue()).to(usageExchange()).with(MqNames.Usage.KEY);
    }

    // ==================== 文件夹打包 ====================

    @Bean
    public TopicExchange folderDownloadExchange() {
        return ExchangeBuilder.topicExchange(MqNames.FolderDownload.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue folderDownloadQueue() {
        return QueueBuilder.durable(MqNames.FolderDownload.QUEUE).withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding bindFolderDownload() {
        return BindingBuilder.bind(folderDownloadQueue()).to(folderDownloadExchange())
                .with(MqNames.FolderDownload.KEY);
    }
}