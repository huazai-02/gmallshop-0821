package com.atguigu.gmall.scheduled.jobHandler;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.scheduled.mapper.CartMapper;
import com.atguigu.gmall.scheduled.pojo.Cart;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CartJobHandler {


    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private StringRedisTemplate  redisTemplate;

    private static final String EXCEPTION_KEY = "cart:exception:info";

    private static final String KEY_PREFIX = "cart:info:";


    @XxlJob("CartSyncDataHandler")
    public ReturnT<String> SyncData(String param){
        //定义定时任务：定时将 redis中写入数据库异常的购物车数据同步到mysql

        if (!this.redisTemplate.hasKey(EXCEPTION_KEY)) {
            return ReturnT.SUCCESS;
        }
        BoundSetOperations<String, String> setOps = this.redisTemplate.boundSetOps(EXCEPTION_KEY);
        String userId = setOps.pop();//从set中pop出一个元素。
        while (StringUtils.isNotBlank(userId)){
            // 1.先删除Mysql中对应的购物车记录
            this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id", userId));
            // 2.查询redis中对应用户的购物车记录
            BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
            // 3.判断该用户redis中的购物车是否为空
            if (hashOps.size()==null){
                return ReturnT.SUCCESS;
            }
            // 4.不为空，对muysql新增数据
            hashOps.values().forEach(cartJson->{
               Cart cart = JSON.parseObject(cartJson.toString(),Cart.class);
               this.cartMapper.insert(cart);
            });

            //获取下一个用户，进行数据同步
            userId = setOps.pop();
        }
        return ReturnT.SUCCESS;
    }
}
