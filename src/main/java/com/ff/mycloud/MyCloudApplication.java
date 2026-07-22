package com.ff.mycloud;


import com.ff.mycloud.ai.config.AIProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;


/**
 * MyCloud网盘系统Spring Boot启动类
 *
 * 启用自动配置、组件扫描和MyBatis Mapper扫描。
 * 配置Jackson2Json消息转换器用于RabbitMQ消息的JSON序列化/反序列化。
 *
 * <p>待优化项（TODO）：
 * <ul>
 *   <li>FileController里手动new Thread改成ThreadPoolTaskExecutor</li>
 *   <li>邮箱验证码发送异步化</li>
 *   <li>ES索引同步异步化</li>
 *   <li>文件夹下载压缩异步化或限流</li>
 *   <li>引入MQ做更可靠的异步任务（已完成）</li>
 * </ul>
 *
 * @author 飞飞
 * @date 2026/3/14
 */
@SpringBootApplication()
@MapperScan("com.ff.mycloud.repositorys")
@MapperScan("com.ff.mycloud.ai.mapper")
@EnableConfigurationProperties(AIProperties.class)
public class MyCloudApplication {

    /**
     * 应用启动入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MyCloudApplication.class, args);
        finishInfoPrint();
    }

    /**
     * 应用启动完成后打印完成信息
     */
    private static void finishInfoPrint() {
        System.out.println();
        System.out.println("-----------------------------------");
        System.out.println("网盘后端启动完成!!");
        System.out.println("-----------------------------------");
    }

    /**
     * 配置RabbitMQ消息转换器
     *
     * 使用Jackson2JsonMessageConverter将Java对象与JSON之间进行自动转换，
     * 替代默认的SimpleMessageConverter，支持复杂对象的序列化传输。
     *
     * @return Jackson2Json消息转换器实例
     */
    @Bean
    MessageConverter createMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}