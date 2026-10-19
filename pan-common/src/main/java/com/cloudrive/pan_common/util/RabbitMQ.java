package com.cloudrive.pan_common.util;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ消息队列测试类，演示RabbitMQ的基本连接、队列声明和消息绑定操作。
 * 该类为开发测试用途，入口方法中包含了连接工厂配置和队列初始化逻辑。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
public class RabbitMQ {
    /** 队列名称 */
    public static String QUEUE_NAME = "hello";

    /**
     * 测试入口，演示RabbitMQ连接与队列操作
     *
     * @param args 命令行参数
     * @throws IOException      IO异常
     * @throws TimeoutException 超时异常
     */
    public static void main(String[] args) throws IOException, TimeoutException {
        // 创建连接工厂并配置连接参数
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("47.95.192.221");
        factory.setPort(5672);
        factory.setVirtualHost("feicloud");
        factory.setUsername("mycloud");
        factory.setPassword("2005525a");

        // 创建连接和内部通道
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        /*
         * 消费者消费消息的参数说明：
         * 1. 消费的队列名称
         * 2. 消费成功之后是否要自动应答(true代表自动应答，false代表手动应答)
         * 3. 消费者消费消息的回调(函数式接口)
         * 4. 消费者取消消费的回调(函数式接口)
         */
        // 声明队列（非持久、非独占、非自动删除）
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        // 将队列绑定到默认交换机，routingKey为队列名
        channel.queueBind("", QUEUE_NAME, "");
    }
}
