package com.ff.mycloud.mq.config;

import com.ff.mycloud.common.constant.ESConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 交换机和队列声明
 * <p>独立于消费者声明，确保 ES.topic exchange 始终存在，即使 ES 未启用也不会
 * 导致 producer 端 ConnectionException。</p>
 *
 * @author 飞飞
 * @date 2026/07/10
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange esTopicExchange() {
        return ExchangeBuilder.topicExchange(ESConstants.EXCHANGE.TOPIC_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue esInsertQueue() {
        return QueueBuilder.durable(ESConstants.QUEUE.INSERT_QUEUE).build();
    }

    @Bean
    public Queue esDeleteQueue() {
        return QueueBuilder.durable(ESConstants.QUEUE.DELETE_QUEUE).build();
    }

    @Bean
    public Queue esUpdateQueue() {
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
}
