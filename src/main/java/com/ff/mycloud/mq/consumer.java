package com.ff.mycloud.mq;

import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.service.UserCenterMapperService;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class consumer {

    @Autowired
    UserCenterMapperService userCenterMapperService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue("user.capacity.queue"),
                    exchange = @Exchange("user.capacity.exchange"),
                    key = "capacity"
            )
    )
    public void updateCapacity(UserCenter message) {
        //更新用户信息
        userCenterMapperService.updateById(message);
    }
}
