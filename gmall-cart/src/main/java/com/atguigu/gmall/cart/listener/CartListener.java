package com.atguigu.gmall.cart.listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class CartListener {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String PRICE_PREFIX = "cart:price:";
    private static final String KEY_PREFIX = "cart:info:";

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "CART_PRICE_QUEUE",durable = "true"),
            exchange = @Exchange(value = "PMS_ITEM_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key ={"item.update"}
    ))
    public void listener(Long spuId, Channel channel, Message message) throws IOException {

        if (spuId==null){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        ResponseVo<List<SkuEntity>> listResponseVo = this.pmsClient.getSkuBySpuId(spuId);
        List<SkuEntity> skuEntityList = listResponseVo.getData();
        // 如果spu下sku为空，直接确认消息，并结束
        if (CollectionUtils.isEmpty(skuEntityList)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }

        skuEntityList.forEach(skuEntity -> {
            if (this.redisTemplate.hasKey(PRICE_PREFIX+skuEntity.getId())){
                this.redisTemplate.opsForValue().set(PRICE_PREFIX+skuEntity.getId(), skuEntity.getPrice().toString());
            }
        });
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);

    }



    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "CART_DELETE_QUEUE",durable = "true"),
            exchange = @Exchange(value = "ORDER_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key ={"cart.delete"}
    ))
    public void deleteCart(Map<String,Object> map, Channel channel, Message message) throws IOException {

        if (CollectionUtils.isEmpty(map)){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            return;
        }
        // 获取用户的id 和 skuIDs
        String userId = map.get("userId").toString();
        List<String> skuIds = JSON.parseArray(map.get("skuIds").toString(),String.class);
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX+userId);
        hashOps.delete(skuIds.toArray());// 将集合转为数组
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);

    }
}
