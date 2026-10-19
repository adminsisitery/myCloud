package com.cloudrive.fileDownloadService.config;

import com.cloudrive.fileDownloadService.common.FolderDownloadConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * RabbitMQ 配置（文件夹打包任务生产端）
 * <p>
 * 只声明打包任务需要的 DLX 与 folder.download.* 队列/交换机；任务消息体为
 * taskId 字符串，统一使用 Jackson JSON 转换器，与 file-async-service 消费端一致。
 * 队列参数（DLX）必须与异步服务、单体的声明保持一致，否则 Broker 会报参数不匹配。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Configuration
public class RabbitMQConfig {

    /**
     * JSON 消息转换器
     * <p>
     * 与 file-async-service 的同名 Bean 对齐，保证 String 任务ID 以 application/json
     * 投递、被 Jackson2JsonMessageConverter 正常反序列化。
     * </p>
     *
     * @return JSON 消息转换器
     */
    @Bean
    public MessageConverter createMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 死信交换机
     *
     * @return 死信 Topic 交换机
     */
    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(FolderDownloadConstants.DLX_EXCHANGE);
    }

    /**
     * 死信队列
     *
     * @return 死信队列
     */
    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(FolderDownloadConstants.DLX_QUEUE).build();
    }

    /**
     * 死信队列绑定
     *
     * @return 绑定关系
     */
    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with(FolderDownloadConstants.DLX_ROUTING_KEY);
    }

    /**
     * 文件夹打包任务交换机
     *
     * @return Topic 交换机
     */
    @Bean
    public TopicExchange folderDownloadExchange() {
        return new TopicExchange(FolderDownloadConstants.EXCHANGE);
    }

    /**
     * 文件夹打包任务队列
     * <p>队列带死信参数，消费失败超过重试次数后进入 dlx:exchange。</p>
     *
     * @return 持久化队列
     */
    @Bean
    public Queue folderDownloadQueue() {
        return QueueBuilder.durable(FolderDownloadConstants.QUEUE)
                .withArguments(dlxArgs())
                .build();
    }

    /**
     * 文件夹打包任务绑定
     *
     * @return 绑定关系
     */
    @Bean
    public Binding folderDownloadBinding() {
        return BindingBuilder.bind(folderDownloadQueue())
                .to(folderDownloadExchange())
                .with(FolderDownloadConstants.ROUTING_KEY);
    }

    /**
     * 死信队列参数（与单体/异步服务一致）
     *
     * @return 队列参数
     */
    private Map<String, Object> dlxArgs() {
        return Map.of(
                "x-dead-letter-exchange", FolderDownloadConstants.DLX_EXCHANGE,
                "x-dead-letter-routing-key", FolderDownloadConstants.DLX_ROUTING_KEY
        );
    }
}
