package com.cloudrive.pan_common.mq;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RabbitMqConverterConfig {

    @Bean
    public MessageConverter createMessageConverter(){
        return new Jackson2JsonMessageConverter();
    }
}
