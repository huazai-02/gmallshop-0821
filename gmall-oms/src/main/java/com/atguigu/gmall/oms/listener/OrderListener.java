package com.atguigu.gmall.oms.listener;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.rabbitmq.client.Channel;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderListener {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "OMS_DISABLE_QUEUE",durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = "order.disable"
    ))
    public void disabledOrder(String orderToken, Channel channel, Message message) throws IOException {
        if (StringUtils.isBlank(orderToken)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }
        // 标记无效订单，更新
        this.orderMapper.updateStatus(orderToken, 0, 5);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }



    @RabbitListener(queues = "ORDER_DEAD_QUEUE")
    public void closeOrder(String orderToken, Channel channel, Message message) throws IOException {
        if (StringUtils.isBlank(orderToken)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }
        // 更新订单状态为关闭状态,关单成功的情况下需要给oms发送消息解锁库存
        if(this.orderMapper.updateStatus(orderToken, 0, 4)==1){
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.disable", orderToken);
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }




    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "OMS_SUCCESS_QUEUE",durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = "order.success"
    ))
    public void successOrder(String orderToken, Channel channel, Message message) throws IOException {
        if (StringUtils.isBlank(orderToken)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }
        // 更新订单为代发货状态，如果更新成功，要发送消息给wms减库存
        if (this.orderMapper.updateStatus(orderToken, 0, 1)==1){
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "stock.minus", orderToken);
        };
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
