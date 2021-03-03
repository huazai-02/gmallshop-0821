package com.atguigu.gmall.payment.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@Slf4j
public class RabbitConfig {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init(){
        // 确认是否到交换机
        this.rabbitTemplate.setConfirmCallback((correlationData,  ack, cause)->{
            if (ack){
                log.error("消息没有成功到达交换机，失败的原因:{}"+cause);
            }
        });

        // 确认是否到达队列(如果来到这个方法就说明没有到达队列)
        this.rabbitTemplate.setReturnCallback(( message,  replyCode,  replyText,  exchange,  routingKey)->{
            log.error("消息没有成功到达队列,消息内容:{}，交换机:{},路由键:{}"+new String(message.getBody()),exchange,routingKey);
        });
    }

}
