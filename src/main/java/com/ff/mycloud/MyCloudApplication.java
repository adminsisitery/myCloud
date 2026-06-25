package com.ff.mycloud;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;


/**
  todo:
 * FileController 里手动 new Thread 改成 ThreadPoolTaskExecutor
 * 邮箱验证码发送异步化
 * ES 索引同步异步化
 * 文件夹下载压缩异步化或限流
 * 后面再引 MQ 做更可靠的异步任务
 */



@SpringBootApplication()
@MapperScan("com.ff.mycloud.repositorys")
public class MyCloudApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyCloudApplication.class, args);
        finishInfoPrint();
    }
    private static void finishInfoPrint() {
        System.out.println();
        System.out.println("-----------------------------------");
        System.out.println("网盘后端启动完成!!");
        System.out.println("-----------------------------------");
    }

    //消息转换器
    @Bean
    MessageConverter createMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}