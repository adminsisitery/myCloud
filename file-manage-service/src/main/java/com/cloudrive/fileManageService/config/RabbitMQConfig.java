package com.cloudrive.fileManageService.config;

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
 * RabbitMQ 声明（文件管理服务生产端）
 * <p>
 * 管理服务的 MQ 只负责“发送”：重命名 / 软删除 / 恢复 / 清空回收站后，
 * 把 ES 索引、PG 向量、操作日志的变更消息投递给 file-async-service 消费。
 * 声明与 file-async-service 完全一致的交换机 / 队列 / 绑定（含 DLX 参数），
 * 避免重复声明参数不一致导致 PRECONDITION_FAILED。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Configuration
public class RabbitMQConfig {

    /** 与异步服务保持一致的 JSON 消息转换器（@RabbitListener 端按 __TypeId__ 反序列化） */
    @Bean
    public MessageConverter createMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    private static Map<String, Object> dlxArgs() {
        return Map.of(
                "x-dead-letter-exchange", MqNames.Dlq.EXCHANGE,
                "x-dead-letter-routing-key", MqNames.Dlq.KEY
        );
    }

    // ==================== 死信路由 ====================

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(MqNames.Dlq.EXCHANGE);
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(MqNames.Dlq.QUEUE).build();
    }

    @Bean
    public Binding bindDlx(Queue dlxQueue, TopicExchange dlxExchange) {
        return BindingBuilder.bind(dlxQueue).to(dlxExchange).with(MqNames.Dlq.KEY);
    }

    // ==================== ES 索引同步 ====================

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
    public Binding bindEsInsert(Queue esInsertQueue, TopicExchange esExchange) {
        return BindingBuilder.bind(esInsertQueue).to(esExchange).with(MqNames.Es.INSERT_KEY);
    }

    @Bean
    public Binding bindEsDelete(Queue esDeleteQueue, TopicExchange esExchange) {
        return BindingBuilder.bind(esDeleteQueue).to(esExchange).with(MqNames.Es.DELETE_KEY);
    }

    @Bean
    public Binding bindEsUpdate(Queue esUpdateQueue, TopicExchange esExchange) {
        return BindingBuilder.bind(esUpdateQueue).to(esExchange).with(MqNames.Es.UPDATE_KEY);
    }

    // ==================== PG 向量同步 ====================

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
    public Binding bindPgInsert(Queue pgInsertQueue, DirectExchange pgExchange) {
        return BindingBuilder.bind(pgInsertQueue).to(pgExchange).with(MqNames.Pg.INSERT_KEY);
    }

    @Bean
    public Binding bindPgDelete(Queue pgDeleteQueue, DirectExchange pgExchange) {
        return BindingBuilder.bind(pgDeleteQueue).to(pgExchange).with(MqNames.Pg.DELETE_KEY);
    }

    // ==================== 操作日志 ====================

    @Bean
    public DirectExchange logExchange() {
        return new DirectExchange(MqNames.Log.EXCHANGE);
    }

    @Bean
    public Queue logQueue() {
        return QueueBuilder.durable(MqNames.Log.QUEUE).build();
    }

    @Bean
    public Binding bindLog(Queue logQueue, DirectExchange logExchange) {
        return BindingBuilder.bind(logQueue).to(logExchange).with(MqNames.Log.KEY);
    }
}
