package com.atguigu.gmall.oms.mapper;

import com.atguigu.gmall.oms.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.swagger.models.auth.In;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单
 * 
 * @author ahua
 * @email ahua@atguigu.com
 * @date 2021-02-28 22:07:24
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    Integer updateStatus(@Param("orderToken") String orderToken, @Param("expectStatus")Integer expectStatus, @Param("targetStatus")Integer targetStatus);
}
