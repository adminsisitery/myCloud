package com.cloudrive.fileService.config;

import com.cloudrive.pan_common.constant.MqNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * MQ 声明（上传服务作为生产端）
 * <p>
 * 队列名/路由键/参数必须与 file-async-service 的 RabbitMQConfig 完全一致，
 * 否则同一队列重复声明时参数不一致会触发 PRECONDITION_FAILED 导致连接被关闭。
 * 这里只声明上传服务会用到的交换机与队列：ES 三队列、PG 两队列、操作日志、用量通知（带 DLX）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 死信参数：与异步服务的 dlxArgs() 保持一致
     */
    private static Map<String, Object> dlxArgs() {
        return Map.of(
                "x-dead-letter-exchange", MqNames.Dlq.EXCHANGE,
                "x-dead-letter-routing-key", MqNames.Dlq.KEY
        );
    }

    // ---------------- 死信 ----------------

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(MqNames.Dlq.EXCHANGE);
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(MqNames.Dlq.QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(MqNames.Dlq.KEY);
    }

    // ---------------- ES 索引同步 ----------------

    @Bean
    public TopicExchange esExchange() {
        return new TopicExchange(MqNames.Es.EXCHANGE);
    }

    @Bean
    public Queue esInsertQueue() {
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
    public Binding esInsertBinding() {
        return BindingBuilder.bind(esInsertQueue()).to(esExchange()).with(MqNames.Es.INSERT_KEY);
    }

    @Bean
    public Binding esDeleteBinding() {
        return BindingBuilder.bind(esDeleteQueue()).to(esExchange()).with(MqNames.Es.DELETE_KEY);
    }

    @Bean
    public Binding esUpdateBinding() {
        return BindingBuilder.bind(esUpdateQueue()).to(esExchange()).with(MqNames.Es.UPDATE_KEY);
    }

    // ---------------- PG 向量同步 ----------------

    @Bean
    public DirectExchange pgExchange() {
        return new DirectExchange(MqNames.Pg.EXCHANGE);
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
    public Binding pgInsertBinding() {
        return BindingBuilder.bind(pgInsertQueue()).to(pgExchange()).with(MqNames.Pg.INSERT_KEY);
    }

    @Bean
    public Binding pgDeleteBinding() {
        return BindingBuilder.bind(pgDeleteQueue()).to(pgExchange()).with(MqNames.Pg.DELETE_KEY);
    }

    // ---------------- 操作日志 ----------------

    @Bean
    public DirectExchange logExchange() {
        return new DirectExchange(MqNames.Log.EXCHANGE);
    }

    @Bean
    public Queue logQueue() {
        return QueueBuilder.durable(MqNames.Log.QUEUE).build();
    }

    @Bean
    public Binding logBinding() {
        return BindingBuilder.bind(logQueue()).to(logExchange()).with(MqNames.Log.KEY);
    }

    // ---------------- 用量通知（带死信） ----------------

    @Bean
    public TopicExchange usageExchange() {
        return new TopicExchange(MqNames.Usage.EXCHANGE);
    }

    @Bean
    public Queue usageQueue() {
        return QueueBuilder.durable(MqNames.Usage.QUEUE).withArguments(dlxArgs()).build();
    }

    @Bean
    public Binding usageBinding() {
        return BindingBuilder.bind(usageQueue()).to(usageExchange()).with(MqNames.Usage.KEY);
    }

    /**
     * 与异步服务同款消息转换器：RabbitTemplate 自动使用该 Bean，
     * 消息体以 JSON 发送并写入 __TypeId__（pan-common 共享消息类）。
     */
    @Bean
    public MessageConverter createMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
