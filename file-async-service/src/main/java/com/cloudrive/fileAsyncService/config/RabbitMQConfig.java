package com.cloudrive.fileAsyncService.config;

import com.cloudrive.fileAsyncService.common.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 声明（异步服务只声明自己消费的队列）
 * <p>
 * 声明 ES / PG / 操作日志 / 用量通知 / 文件夹打包五类交换机和队列，以及公用的死信交换机/队列。
 * 队列名与单体完全一致：单体生产端（上传/管理/分享等链路）无需改动即可被本服务消费。
 * 因为 @RabbitListener 的消息体反序列化依赖 JSON 转换器，这里同时声明与单体一致的
 * Jackson2JsonMessageConverter Bean。
 * </p>
 */
@Configuration
public class RabbitMQConfig {

    /** 死信参数模板 */
    private static Map<String, Object> dlxArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", MqConstants.Dlq.EXCHANGE);
        args.put("x-dead-letter-routing-key", MqConstants.Dlq.KEY);
        return args;
    }

    /** 消息体 JSON 序列化，与单体保持一致，保证跨服务消息可反序列化 */
    @Bean
    public MessageConverter createMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ==================== 死信基础设施 ====================

    @Bean
    public TopicExchange dlxExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.Dlq.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(MqConstants.Dlq.QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(MqConstants.Dlq.KEY);
    }

    // ==================== ES 索引同步 ====================

    @Bean
    public TopicExchange esTopicExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.Es.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue esInsertQueue() {
        // DLX 由 Broker 上的 mycloud-legacy-dlx policy 配置，此处不能追加声明参数
        return QueueBuilder.durable(MqConstants.Es.INSERT_QUEUE).build();
    }

    @Bean
    public Queue esDeleteQueue() {
        return QueueBuilder.durable(MqConstants.Es.DELETE_QUEUE).build();
    }

    @Bean
    public Queue esUpdateQueue() {
        return QueueBuilder.durable(MqConstants.Es.UPDATE_QUEUE).build();
    }

    @Bean
    public Binding bindEsInsert() {
        return BindingBuilder.bind(esInsertQueue()).to(esTopicExchange()).with(MqConstants.Es.INSERT_KEY);
    }

    @Bean
    public Binding bindEsDelete() {
        return BindingBuilder.bind(esDeleteQueue()).to(esTopicExchange()).with(MqConstants.Es.DELETE_KEY);
    }

    @Bean
    public Binding bindEsUpdate() {
        return BindingBuilder.bind(esUpdateQueue()).to(esTopicExchange()).with(MqConstants.Es.UPDATE_KEY);
    }

    // ==================== PG 向量同步 ====================

    @Bean
    public DirectExchange pgTopicExchange() {
        return ExchangeBuilder.directExchange(MqConstants.Pg.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue pgInsertQueue() {
        return QueueBuilder.durable(MqConstants.Pg.INSERT_QUEUE).build();
    }

    @Bean
    public Queue pgDeleteQueue() {
        return QueueBuilder.durable(MqConstants.Pg.DELETE_QUEUE).build();
    }

    @Bean
    public Binding bindPgInsert() {
        return BindingBuilder.bind(pgInsertQueue()).to(pgTopicExchange()).with(MqConstants.Pg.INSERT_KEY);
    }

    @Bean
    public Binding bindPgDelete() {
        return BindingBuilder.bind(pgDeleteQueue()).to(pgTopicExchange()).with(MqConstants.Pg.DELETE_KEY);
    }

    // ==================== 操作日志 ====================

    @Bean
    public DirectExchange logTopicExchange() {
        return ExchangeBuilder.directExchange(MqConstants.Log.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue logQueue() {
        return QueueBuilder.durable(MqConstants.Log.QUEUE).build();
    }

    @Bean
    public Binding bindLog() {
        return BindingBuilder.bind(logQueue()).to(logTopicExchange()).with(MqConstants.Log.KEY);
    }

    // ==================== 用量通知 ====================

    @Bean
    public TopicExchange usageExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.Usage.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue usageQueue() {
        return QueueBuilder.durable(MqConstants.Usage.QUEUE).withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding bindUsage() {
        return BindingBuilder.bind(usageQueue()).to(usageExchange()).with(MqConstants.Usage.KEY);
    }

    // ==================== 文件夹打包 ====================

    @Bean
    public TopicExchange folderDownloadExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.FolderDownload.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue folderDownloadQueue() {
        return QueueBuilder.durable(MqConstants.FolderDownload.QUEUE).withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding bindFolderDownload() {
        return BindingBuilder.bind(folderDownloadQueue()).to(folderDownloadExchange())
                .with(MqConstants.FolderDownload.KEY);
    }
}
