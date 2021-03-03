package com.atguigu.gmall.search.listener;


import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.api.GmallWmsApi;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GoodSListener {

    @Autowired
    private GmallPmsClient gmallPmsClient;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private GmallWmsApi gmallWmsApi;


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "SEARCH_INSERT_QUEUE",durable = "true"),
            exchange = @Exchange(value = "PMS_ITEM_EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"item.insert"}
    ))
    public void listener(Long spuId, Channel channel, Message message) throws IOException {

        if (spuId==null){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
        //查询spu
        ResponseVo<SpuEntity> spuEntityResponseVo = this.gmallPmsClient.querySpuById(spuId);
        SpuEntity spuEntity = spuEntityResponseVo.getData();
        if (spuEntity==null){
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        ResponseVo<List<SkuEntity>> skus = gmallPmsClient.getSkuBySpuId(spuEntity.getId());
        List<SkuEntity> skuEntities = skus.getData();

        if (!CollectionUtils.isEmpty(skuEntities)) {

            List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                Goods goods = new Goods();

                //创建时间
                goods.setCreateTime(spuEntity.getCreateTime());

                // sku相关信息
                goods.setDefaultImage(skuEntity.getDefaultImage());
                goods.setTitle(skuEntity.getTitle());
                goods.setSubTitle(skuEntity.getSubtitle());
                goods.setPrice(skuEntity.getPrice().doubleValue());
                goods.setSkuId(skuEntity.getId());

                //获取库存：销量和是否有货
                ResponseVo<List<WareSkuEntity>> ware = this.gmallWmsApi.getWareSkuBySkuId(skuEntity.getId());
                List<WareSkuEntity> wareSkuEntityList = ware.getData();
                if (!CollectionUtils.isEmpty(wareSkuEntityList)) {
                    goods.setSales(wareSkuEntityList.stream().map(WareSkuEntity::getSales).reduce((a, b) -> a + b).get());
                    goods.setStore(wareSkuEntityList.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getSales() - wareSkuEntity.getStockLocked() > 0));
                }

                //品牌
                ResponseVo<BrandEntity> brandEntityResponseVo = gmallPmsClient.queryBrandById(skuEntity.getBrandId());
                BrandEntity brandEntity = brandEntityResponseVo.getData();
                if (brandEntity != null) {
                    goods.setBrandName(brandEntity.getName());
                    goods.setBrandId(brandEntity.getId());
                    goods.setLogo(brandEntity.getLogo());
                }

                //分类
                ResponseVo<CategoryEntity> categoryEntityResponseVo = gmallPmsClient.queryCategoryById(skuEntity.getCategoryId());
                CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
                if (categoryEntity != null) {
                    goods.setCategoryId(categoryEntity.getId());
                    goods.setCategoryName(categoryEntity.getName());
                }
                //检索参数
                List<SearchAttrValue> searchAttrList = new ArrayList<>();
                ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = gmallPmsClient.querySearchAttrValuesByCidAndSkuId(categoryEntity.getId(), skuEntity.getId());
                List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
                if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                    searchAttrList.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                        SearchAttrValue searchAttrValue = new SearchAttrValue();
                        BeanUtils.copyProperties(skuAttrValueEntity, searchAttrValue);
                        return searchAttrValue;
                    }).collect(Collectors.toList()));

                }

                ResponseVo<List<SpuAttrValueEntity>> baseAttrResponseVo = gmallPmsClient.querySearchAttrValueByCidAndSpuId(categoryEntity.getId(), spuEntity.getId());
                List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrResponseVo.getData();
                if (!CollectionUtils.isEmpty(spuAttrValueEntities)) {
                    searchAttrList.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                        SearchAttrValue searchAttrValue = new SearchAttrValue();
                        BeanUtils.copyProperties(spuAttrValueEntity, searchAttrValue);
                        return searchAttrValue;
                    }).collect(Collectors.toList()));
                }
                goods.setSearchAttrs(searchAttrList);
                return goods;
            }).collect(Collectors.toList());
            this.goodsRepository.saveAll(goodsList);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

}
