package com.atguigu.gmall.order.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.pojo.OrderConfirmVo;
import com.atguigu.gmall.oms.pojo.OrderItemVo;
import com.atguigu.gmall.oms.pojo.OrderSubmitVo;
import com.atguigu.gmall.order.pojo.UserInfo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.api.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallUmsClient umsClient;
    @Autowired
    private GmallCartClient cartClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GmallOmsClient omsClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;


    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX="order:token:";

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;


    public OrderConfirmVo confirm() {

        OrderConfirmVo confirmVo = new OrderConfirmVo();

        // 获取登录用户的ID
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        CompletableFuture<List<Cart>> cartFuture = CompletableFuture.supplyAsync(() -> {
            ResponseVo<List<Cart>> cartResponseVo = this.cartClient.queryCheckedCarts(userId);
            List<Cart> carts = cartResponseVo.getData();
            if (CollectionUtils.isEmpty(carts)) {
                throw new OrderException("您没有选中的购物记录");
            }
            return carts;
        }, threadPoolExecutor);

        CompletableFuture<Void> itemFuture = cartFuture.thenAcceptAsync(carts -> {
            List<OrderItemVo> itemVos = carts.stream().map(cart -> {
                OrderItemVo itemVo = new OrderItemVo();
                //这里只获取购物车中的sku和count，因为其他数据可能和数据库实时数据不同
                itemVo.setCount(cart.getCount());
                itemVo.setSkuId(cart.getSkuId());

                CompletableFuture<Void> skuFuture = CompletableFuture.runAsync(() -> {
                    //查询sku信息
                    ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
                    SkuEntity skuEntity = skuEntityResponseVo.getData();
                    if (skuEntity != null) {
                        itemVo.setDefaultImage(skuEntity.getDefaultImage());
                        itemVo.setPrice(skuEntity.getPrice());
                        itemVo.setWeight(skuEntity.getWeight());
                        itemVo.setTitle(skuEntity.getTitle());
                    }
                }, threadPoolExecutor);

                CompletableFuture<Void> saleAttrFuture = CompletableFuture.runAsync(() -> {
                    //查询销售属性
                    ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querysaleAttrValuesBySkuId(cart.getSkuId());
                    List<SkuAttrValueEntity> attrValueEntityList = saleAttrResponseVo.getData();
                    itemVo.setSaleAttrs(attrValueEntityList);
                }, threadPoolExecutor);

                CompletableFuture<Void> salesFuture = CompletableFuture.runAsync(() -> {
                    //查询营销信息
                    ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
                    List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
                    itemVo.setSales(itemSaleVos);

                }, threadPoolExecutor);

                CompletableFuture<Void> wareFuture = CompletableFuture.runAsync(() -> {
                    //查询库存
                    ResponseVo<List<WareSkuEntity>> wareSkuResponseVo = this.wmsClient.getWareSkuBySkuId(cart.getSkuId());
                    List<WareSkuEntity> wareSkuEntityList = wareSkuResponseVo.getData();
                    if (!CollectionUtils.isEmpty(wareSkuEntityList)) {
                        itemVo.setStore(wareSkuEntityList.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
                    }
                }, threadPoolExecutor);

                CompletableFuture.allOf(wareFuture,salesFuture,saleAttrFuture,skuFuture).join();
                return itemVo;
            }).collect(Collectors.toList());
            confirmVo.setOrderItem(itemVos);
            }, threadPoolExecutor);



        // 根据用户ID查询用户的收货地址列表
        ResponseVo<List<UserAddressEntity>> addressResponseVo = this.umsClient.queryAddressesByUserId(userId);
        List<UserAddressEntity> addressEntityList = addressResponseVo.getData();
        confirmVo.setAddresses(addressEntityList);

        //根据用户ID获取到积分
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
        UserEntity userEntity = userEntityResponseVo.getData();
        if (userEntity!=null){
            confirmVo.setBounds(userEntity.getIntegration());
        }

        //通过雪花算法工具类获取到唯一标识：防重复，保存到redis中一份。
        String orderToken = IdWorker.getTimeId();
        this.redisTemplate.opsForValue().set(KEY_PREFIX+orderToken,orderToken,24, TimeUnit.HOURS);
        confirmVo.setOrderToken(orderToken);

        return confirmVo;
    }

    // 以下提交订单五个步骤只有 验库存锁库存 和 下单两部分涉及到事务。所以先确保这两个步骤的本地事务成功。
    public void submit(OrderSubmitVo submitVo) {

        // 1.防重：用传入进来的orderToken和redis中保存的orderToken进行比较
        //  查redis中是否有 orderToken时要确保 查和最后删除orderToken原子性。（并发时查到了但还没来得及删除，会防重失败）
        //  使用 lua 脚本确保原子性

        String orderToken = submitVo.getOrderToken();
        if (StringUtils.isBlank(orderToken)){
            throw new OrderException("非法提交");
        }
        String script = "if(redis.call('get',KEYS[1])==ARGV[1]) then return redis.call('del',KEYS[1]) else return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX+orderToken), orderToken);
        if (!flag){
            throw new OrderException("请不要重复提交");
        }
        // 2.验总价: 遍历送货清单，获取数据库的实时价格*送货数量，最后再累加
        BigDecimal totalPrice = submitVo.getTotalPrice();
        List<OrderItemVo> items = submitVo.getItems();
        if(CollectionUtils.isEmpty(items)){
            throw new OrderException("您没有要购买的商品");
        }
        BigDecimal currTotalPrice = items.stream().map(item -> {
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                return new BigDecimal(0);
            }
            return skuEntity.getPrice().multiply(item.getCount());
        }).reduce((a, b) -> a.add(b)).get();
        if (totalPrice.compareTo(currTotalPrice)!=0){
            throw new OrderException("页面已过期，请刷新重试");
        }

        // 3.验库存并锁定库存
        List<SkuLockVo> lockVos= null;
        lockVos = items.stream().map(item->{
            SkuLockVo lockVo = new SkuLockVo();
            lockVo.setSkuId(item.getSkuId());
            lockVo.setCount(item.getCount().intValue());
            return lockVo;
        }).collect(Collectors.toList());
        ResponseVo<List<SkuLockVo>> skuLockResponseVo = this.wmsClient.checkAndLock(lockVos, orderToken);
        List<SkuLockVo> skuLockVos = skuLockResponseVo.getData();
        if (!CollectionUtils.isEmpty(skuLockVos)){
            throw new OrderException(JSON.toJSONString(skuLockVos));
        }
        // 分析1：需要设置定时关单。    (延时队列+死信队列)
        // 分析2：如果锁定库存后服务器宕机，无法下单也无法解锁库存。所以需要定时解锁库存，并且定时定时解锁库存任务执行时间必须在定时关单之后）
        // 分析3：如果这里服务器不宕机，因为有定时解锁任务的存在，再解锁库存后不删除缓存中的库存缓存的情况下会导致重复解锁。

//        int i = 1 / 0;



        // 4.下单
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        try {
            this.omsClient.saveOrder(submitVo,userId);
            //订单正常创建成功的情况下发送消息定时关单
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","order.close",orderToken);
        } catch (Exception e) {
            e.printStackTrace();
            //已经锁定库存，但是下单失败应该立马解锁库存
            //这部分出现异常的两种情况:
            // 1.订单创建失败，本地事务回滚，需要去解锁相应库存
            // 2.订单创建成功，但omsClient调用时超时，将订单设为无效状态，并且解锁库存
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","order.disable", orderToken);
            throw new OrderException("创建订单时服务器错误！");

        }


        // 5.异步删除购物车中对应的记录：异步删除。
        Map<String,Object> map = new HashMap<>();
        map.put("userId", userId);
        List<Long> skuIds = items.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        map.put("skuIds", JSON.toJSONString(skuIds));// 消息在发送过程中，如果太过复杂，需要先序列化进行传递（如：Map<String,List<Long>>）。
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "cart.delete", map);
    }
}
