package com.atguigu.gmall.order.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

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


    /**
     *  定义业务交换机：ORDER_EXCHANGE
     */


    /**
     * 定义延时队列：ORDER_TTL_QUEUE
     * 配置参数:
     * x-message-ttl : 90000
     * x-dead-letter-exchange: ORDER_EXCHANGE
     * x-dead-letter-routing-key: order.dead
     */
    @Bean
    public Queue ttlQueue(){
        return QueueBuilder.durable("ORDER_TTL_QUEUE")
                .withArgument("x-message-ttl",90000)
                .withArgument("x-dead-letter-exchange","ORDER_EXCHANGE")
                .withArgument("x-dead-letter-routing-key","order.dead").build();
    }

    /**
     * 延时队列绑定业务交换机:order.close
     */
    @Bean
    public Binding ttlBinding(){
        return new Binding("ORDER_TTL_QUEUE",Binding.DestinationType.QUEUE,"ORDER_EXCHANGE","order.close",null);
    }

    /**
     * 定义死信交换机：ORDER_EXCHANGE
     */

    /**
     * 定义死信队列：ORDER_DEAD_QUEUE
     *
     */
    @Bean
    public Queue deadQueue(){
        return QueueBuilder.durable("ORDER_DEAD_QUEUE").build();
    }

    /**
     * 死信队列绑定到死信交换机:order.dead
     */
    @Bean
    public Binding deadBinding(){
        return new Binding("ORDER_DEAD_QUEUE",Binding.DestinationType.QUEUE,"ORDER_EXCHANGE","order.dead", null);
    }


}
