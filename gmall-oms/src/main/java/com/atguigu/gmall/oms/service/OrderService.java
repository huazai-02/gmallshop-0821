package com.atguigu.gmall.oms.service;

import com.atguigu.gmall.oms.pojo.OrderSubmitVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.oms.entity.OrderEntity;

/**
 * 订单
 *
 * @author ahua
 * @email ahua@atguigu.com
 * @date 2021-02-28 22:07:24
 */
public interface OrderService extends IService<OrderEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    void saveOrder(OrderSubmitVo submitVo,Long userId);
}

