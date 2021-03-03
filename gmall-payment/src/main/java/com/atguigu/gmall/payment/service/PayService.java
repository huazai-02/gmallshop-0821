package com.atguigu.gmall.payment.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.payment.feign.GmallOmsClient;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.pojo.PayAsyncVo;
import com.atguigu.gmall.payment.pojo.PaymentInfoEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class PayService {

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    public OrderEntity queryOrderByToken(String orderToken) {
        ResponseVo<OrderEntity> orderEntityResponseVo = this.omsClient.queryOrderByToken(orderToken);
        OrderEntity orderEntity = orderEntityResponseVo.getData();
        return orderEntity;
    }

    public String savePayment(OrderEntity orderEntity) {
        PaymentInfoEntity paymentInfoEntity = new PaymentInfoEntity();
        paymentInfoEntity.setPaymentStatus(0);
        paymentInfoEntity.setPaymentType(orderEntity.getBillType());
        paymentInfoEntity.setTotalAmount(new BigDecimal("0.01"));
        paymentInfoEntity.setSubject("谷粒商城支付平台");
        paymentInfoEntity.setOutTradeNo(orderEntity.getOrderSn());
        paymentInfoEntity.setCreateTime(new Date());
        paymentInfoMapper.insert(paymentInfoEntity);
        return paymentInfoEntity.getId().toString();

    }


    public PaymentInfoEntity queryPaymentById(String payId){

        return this.paymentInfoMapper.selectById(payId);
    }

    public int updatePaymentInfo(PayAsyncVo payAsyncVo,String payId) {

        PaymentInfoEntity paymentInfoEntity = this.paymentInfoMapper.selectById(payId);
        paymentInfoEntity.setTradeNo(payAsyncVo.getTrade_no());
        paymentInfoEntity.setPaymentStatus(1);
        paymentInfoEntity.setCallbackTime(new Date());
        paymentInfoEntity.setCallbackContent(JSON.toJSONString(payAsyncVo));
        return this.paymentInfoMapper.updateById(paymentInfoEntity);
    }
}
