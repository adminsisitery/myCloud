package com.ff.mycloud.common.util;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RabbitMQ {
    // 队列名
    public static String QUEUE_NAME = "hello";

    public static void main(String[] args) throws IOException, TimeoutException {
        // 创建工厂
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("47.95.192.221");
        factory.setPort(5672);
        factory.setVirtualHost("feicloud");
        factory.setUsername("mycloud");
        factory.setPassword("2005525a");

        // 创建connection
        Connection connection = factory.newConnection();
        //创建内部通道
        Channel channel = connection.createChannel();
        /*
         * 消费者消费消息
         * 1.消费的队列名称
         * 2.消费成功之后是否要自动应答(true代表自动应答,false代表手动应答)
         * 3.消费者消费消息的回调(函数式接口)
         * 4.消费者取消消费的回调(函数式接口)
         */
        // 声明队列
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        // 发送消息
        //              默认交换机   队列名     message
        channel.queueBind("", QUEUE_NAME, "");
    }
}
