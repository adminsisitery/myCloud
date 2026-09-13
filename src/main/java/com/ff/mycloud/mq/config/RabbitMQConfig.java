package com.ff.mycloud.mq.config;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.constant.ESConstants;
import org.springframework.amqp.core.DirectExchange;
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
 * <p>ES、PG、操作日志历史队列通过 RabbitMQ policy 配置 DLX，避免与已存在队列的参数冲突。
 * 用量通知和文件夹下载队列保留声明式 DLX 参数。部署策略见 docs/rabbitmq-topology.md。
 * 配合 default-requeue-rejected=false，拒绝的消息按死信路由进入 DLQ。</p>
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
        // 历史队列的 DLX 由 mycloud-legacy-dlx policy 配置，不能追加声明参数。
        return QueueBuilder.durable(ESConstants.QUEUE.INSERT_QUEUE).build();
    }

    @Bean
    public Queue esDeleteQueue() {
        // 历史队列的 DLX 由 mycloud-legacy-dlx policy 配置，不能追加声明参数。
        return QueueBuilder.durable(ESConstants.QUEUE.DELETE_QUEUE).build();
    }

    @Bean
    public Queue esUpdateQueue() {
        // 历史队列的 DLX 由 mycloud-legacy-dlx policy 配置，不能追加声明参数。
        return QueueBuilder.durable(ESConstants.QUEUE.UPDATE_QUEUE).build();
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
    public DirectExchange pgTopicExchange() {
        return ExchangeBuilder.directExchange(CommonConstants.PG.TOPIC_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue pgInsertQueue() {
        // 历史队列的 DLX 由 mycloud-legacy-dlx policy 配置，不能追加声明参数。
        return QueueBuilder.durable(CommonConstants.PG.INSERT_QUEUE).build();
    }

    @Bean
    public Queue pgDeleteQueue() {
        // 历史队列的 DLX 由 mycloud-legacy-dlx policy 配置，不能追加声明参数。
        return QueueBuilder.durable(CommonConstants.PG.DELETE_QUEUE).build();
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
    public DirectExchange logTopicExchange() {
        return ExchangeBuilder.directExchange(CommonConstants.LogMQ.EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue logQueue() {
        // 历史队列的 DLX 由 mycloud-legacy-dlx policy 配置，不能追加声明参数。
        return QueueBuilder.durable(CommonConstants.LogMQ.QUEUE).build();
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
