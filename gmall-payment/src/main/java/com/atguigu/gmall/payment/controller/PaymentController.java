package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.config.AlipayTemplate;
import com.atguigu.gmall.payment.interceptor.LoginInterceptor;
import com.atguigu.gmall.payment.pojo.PayAsyncVo;
import com.atguigu.gmall.payment.pojo.PayVo;
import com.atguigu.gmall.payment.pojo.PaymentInfoEntity;
import com.atguigu.gmall.payment.pojo.UserInfo;
import com.atguigu.gmall.payment.service.PayService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PaymentController {

    @Autowired
    private PayService payService;
    @Autowired
    private AlipayTemplate alipayTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("pay.html")
    public String toPay(@RequestParam("orderToken")String orderToken, Model model){
        OrderEntity orderEntity = this.payService.queryOrderByToken(orderToken);
        if(orderEntity ==null){
            throw new OrderException("要支付的订单不存在");
        }

        // 判断订单是否属于该用户
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        if (userInfo.getUserId() != orderEntity.getUserId()){
            throw new OrderException("该订单不属于您，或者您没有支付权限");
        }

        // 判断订单是否属于代付款状态
        if (orderEntity.getStatus()!=0){
            throw new OrderException("该订单状态无法支付");
        }
        model.addAttribute("orderEntity",orderEntity);
        return "pay";
    }


    @GetMapping("alipay.html")
    @ResponseBody
    public String alipay(@RequestParam("orderToken")String orderToken) throws AlipayApiException {

        OrderEntity orderEntity = this.payService.queryOrderByToken(orderToken);
        if(orderEntity ==null){
            throw new OrderException("要支付的订单不存在");
        }

        // 判断订单是否属于该用户
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        if (userInfo.getUserId() != orderEntity.getUserId()){
            throw new OrderException("该订单不属于您，或者您没有支付权限");
        }

        // 判断订单是否属于代付款状态
        if (orderEntity.getStatus()!=0){
            throw new OrderException("该订单状态无法支付");
        }

        //调用 阿里的支付接口，跳转到支付界面
        PayVo payVo = new PayVo();
        payVo.setOut_trade_no(orderToken);
        payVo.setTotal_amount("0.01");
        payVo.setSubject("谷粒商城支付平台");
        // 生成对账记录。
        String payId = this.payService.savePayment(orderEntity);
        payVo.setPassback_params(payId);
        String form = this.alipayTemplate.pay(payVo);
        return form;
    }

    // 支付宝支付成功同步返回界面
    @GetMapping("pay/success")
    public String paysuccess(){
        return "paysuccess";
    }


    // 支付宝异步通知商户支付信息; 支付宝要要访问这两个接口；需要内网穿透
    @PostMapping("pay/ok")
    public Object payok(PayAsyncVo payAsyncVo){

        // 1.验签
        Boolean flag = this.alipayTemplate.checkSignature(payAsyncVo);
        if (!flag){
            return "failure";
        }
        // 2.校验业务参数: app_id/out_trade_no/total_amount
        String app_id = payAsyncVo.getApp_id();
        String out_trade_no = payAsyncVo.getOut_trade_no();
        String total_amount = payAsyncVo.getTotal_amount();
        String payId = payAsyncVo.getPassback_param();
        PaymentInfoEntity paymentInfoEntity = this.payService.queryPaymentById(payId);
        if (StringUtils.equals(app_id,this.alipayTemplate.getApp_id())
                ||new BigDecimal(total_amount).compareTo(paymentInfoEntity.getTotalAmount()) !=0
                || StringUtils.equals(out_trade_no, paymentInfoEntity.getOutTradeNo())){
            return "failure";
        }
        // 3.检验支付状态
        String trade_status = payAsyncVo.getTrade_status();
        if (!StringUtils.equals("TRADE_SUCCESS", trade_status)){
            return "failure";
        }

        // 4.更新对账表中状态
        if (this.payService.updatePaymentInfo(payAsyncVo,payId)==0){
            return "failure";
        }


        //5.发送消息给订单(oms 发送消息给 wms 减库存)
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "order.success", out_trade_no);

        // 6.返回消息告诉支付宝结果
        return "success";
    }



    // 秒杀伪代码
    @GetMapping("seckill/{skuId}")
    public ResponseVo<Object> seckill(@PathVariable("skuId")Long skuId){
        RLock fairLock = this.redissonClient.getFairLock("seckill:lock:"+skuId);
        fairLock.lock();
        //判断库存是否充足
        String stockString = this.redisTemplate.opsForValue().get("seckill:lock:" + skuId);
        if (StringUtils.isBlank(stockString) || Integer.parseInt(stockString)==0){
            throw new OrderException("秒杀已结束");
        }
        // 减库存   decrement (key) 对应的value减 1
        this.redisTemplate.opsForValue().decrement("seckill:stokc:"+skuId);
        // 发送消息，异步创建订单并且减少库存
        Map<String, Object> msg = new HashMap<>();
        msg.put("skuId",skuId);
        msg.put("count", 1);
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        msg.put("userId", userId);
        String orderToken = IdWorker.getTimeId();
        msg.put("orderToken",orderToken );

        //秒杀成功后发送信息oms创建订单;  oms应该有一个监听器监听此消息，并且监听器内执行创建订单且发送消息到wms编辑库存的操作。
        //在创建完订单后调用 countDownLatch.countDown(); 放行闭锁阻塞的订单。
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "seckill.success",msg);


        //因为创建订单是异步操作，可能会缓慢创建订单，此时用户查询得到的结果为空。因为需要使用countDownLatch闭锁
        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("seckill:countdown:" + orderToken);
        countDownLatch.trySetCount(1);

        fairLock.unlock();
        return ResponseVo.ok("秒杀成功"+orderToken);
    }

    // 秒杀成功后点击查询订单
    @GetMapping("order/{orderToken}")
    public ResponseVo<OrderEntity> queryOrderByToken(@PathVariable("orderToken")String orderToken) throws InterruptedException {

        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("seckill:countdown:" + orderToken);
        countDownLatch.await();

        UserInfo userInfo = LoginInterceptor.getUserInfo();
        OrderEntity orderEntity = this.payService.queryOrderByToken(orderToken);
        if (orderEntity.getUserId() == userInfo.getUserId()){
            return ResponseVo.ok(orderEntity);
        }
        return ResponseVo.ok();
    }
}
