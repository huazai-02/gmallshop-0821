package com.atguigu.gmall.order.pojo;


import com.atguigu.gmall.oms.pojo.OrderItemVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmVo {

    // 收件人列表
    private List<UserAddressEntity> addresses;

    // 商品信息
    private List<OrderItemVo> orderItem;//送货清单

    // 购买积分
    private Integer bounds;//积分

    private String OrderToken; //防重复提交：购物车页跳转到订单页面时，生成一个唯一的标识，一个响应给页面，一个保存到redis。
                                // 当点击提交订单去结算时，将redis中的删除，并添加判断条件redis中是否有该标识，有的话才可以提交，没有的话响应错误。



}
