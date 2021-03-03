package com.atguigu.gmall.oms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.atguigu.gmall.oms.feign.GmallPmsClient;
import com.atguigu.gmall.oms.feign.GmallSmsClient;
import com.atguigu.gmall.oms.feign.GmallUmsClient;
import com.atguigu.gmall.oms.mapper.OrderItemMapper;
import com.atguigu.gmall.oms.pojo.OrderItemVo;
import com.atguigu.gmall.oms.pojo.OrderSubmitVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.oms.mapper.OrderMapper;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {

    @Autowired
    private OrderItemMapper itemMapper;
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallUmsClient umsClient;


    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<OrderEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<OrderEntity>()
        );

        return new PageResultVo(page);
    }



    @Override
    @Transactional
    public void saveOrder(OrderSubmitVo submitVo, Long userId) {



        List<OrderItemVo> items = submitVo.getItems();
        if (CollectionUtils.isEmpty(items)){
            throw new OrderException("您没有购买的商品信息");
        }

        //新增订单表
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setUserId(userId);
        orderEntity.setOrderSn(submitVo.getOrderToken());
        orderEntity.setCreateTime(new Date());
        orderEntity.setTotalAmount(submitVo.getTotalPrice());
        orderEntity.setStatus(0);
        //TODO:总金额-各种打折
        orderEntity.setPayAmount(submitVo.getTotalPrice());
        orderEntity.setPayType(submitVo.getPayType());
        orderEntity.setSourceType(0);
        orderEntity.setDeliveryCompany(submitVo.getDeliveryCompany());
        orderEntity.setIntegration(1000);
        orderEntity.setGrowth(2000);

        UserAddressEntity address = submitVo.getAddress();
        if (address !=null){
            orderEntity.setReceiverAddress(address.getAddress());
            orderEntity.setReceiverCity(address.getCity());
            orderEntity.setReceiverName(address.getName());
            orderEntity.setReceiverPhone(address.getPhone());
            orderEntity.setReceiverPostCode(address.getPostCode());
            orderEntity.setReceiverCity(address.getCity());
            orderEntity.setReceiverRegion(address.getRegion());
        }
        orderEntity.setDeleteStatus(0);
        orderEntity.setUseIntegration(submitVo.getBounds());
        this.save(orderEntity);
        Long orderEntityId = orderEntity.getId();

        //新增订单详情表
        items.forEach(item->{
            OrderItemEntity orderItemEntity = new OrderItemEntity();
            orderItemEntity.setOrderId(orderEntityId);
            orderItemEntity.setOrderSn(submitVo.getOrderToken());
            // 根据skuId查询sku相关信息
            Long skuId = item.getSkuId();
            ResponseVo<SkuEntity> skuResponseVo = this.pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuResponseVo.getData();
            if (skuEntity!=null){
                orderItemEntity.setSkuId(skuId);
                orderItemEntity.setSkuPic(skuEntity.getDefaultImage());
                orderItemEntity.setSkuName(skuEntity.getName());
                orderItemEntity.setSkuPrice(skuEntity.getPrice());
                orderItemEntity.setSkuQuantity(item.getCount().intValue());
                orderItemEntity.setCategoryId(skuEntity.getCategoryId());
            }
            // 查询sku的销售属性
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVO = this.pmsClient.querysaleAttrValuesBySkuId(skuId);
            List<SkuAttrValueEntity> skuAttrValueEntityList = saleAttrResponseVO.getData();
            orderItemEntity.setSkuAttrsVals(JSON.toJSONString(skuAttrValueEntityList));

            // 查询品牌信息
            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            orderItemEntity.setSpuBrand(brandEntity.getName());

            //查询spu
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity!=null){
                orderItemEntity.setSpuId(spuEntity.getId());
                orderItemEntity.setSpuName(spuEntity.getName());

            }
            // 查询spu的描述信息
            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(spuEntity.getId());
            SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
            if(spuDescEntity!=null){
                orderItemEntity.setSpuPic(spuDescEntity.getDecript());
            }

            // todo:查询商品赠送的积分信息

            this.itemMapper.insert(orderItemEntity);
        });
//        try {
//            TimeUnit.SECONDS.sleep(10);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

    }

}