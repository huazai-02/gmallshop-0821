package com.atguigu.gmall.item.service;


import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.api.controller.GmallSmsApi;
import com.atguigu.gmall.sms.api.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import sun.nio.cs.ext.SJIS;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class ItemService {

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private TemplateEngine templateEngine;

    public ItemVo loadData(Long skuId) {
        ItemVo itemVo = new ItemVo();

        //获取sku相关信息
        CompletableFuture<SkuEntity> skuFuture = CompletableFuture.supplyAsync(() -> {

            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                return null;
            }

            itemVo.setSkuId(skuId);
            itemVo.setTitle(skuEntity.getTitle());
            itemVo.setSubTitle(skuEntity.getSubtitle());
            itemVo.setDefaultImage(skuEntity.getDefaultImage());
            itemVo.setPrice(skuEntity.getPrice());
            itemVo.setWeight(skuEntity.getWeight());
            return skuEntity;
        }, threadPoolExecutor);

        //设置分类信息
        CompletableFuture<Void> catesFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<CategoryEntity>> catesResponseVo = this.pmsClient.query123CategoriesBycid3(skuEntity.getCategoryId());
            List<CategoryEntity> categoryEntities = catesResponseVo.getData();
            itemVo.setCategories(categoryEntities);
        }, threadPoolExecutor);

        //设置品牌信息
        CompletableFuture<Void> brandFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            if (brandEntity != null) {
                itemVo.setBrandId(brandEntity.getId());
                itemVo.setBrandName(brandEntity.getName());
            }
        }, threadPoolExecutor);

        //spu信息
        CompletableFuture<Void> spuFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null) {
                itemVo.setSpuId(spuEntity.getId());
                itemVo.setSpuName(spuEntity.getName());
            }
        }, threadPoolExecutor);

        //spu图片列表
        CompletableFuture<Void> spuImagesFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<SkuImagesEntity>> skuResponseVo = this.pmsClient.queryImagesBySkuId(skuEntity.getSpuId());
            List<SkuImagesEntity> skuImagesEntities = skuResponseVo.getData();
            itemVo.setImages(skuImagesEntities);
        }, threadPoolExecutor);

        //sku的营销信息
        CompletableFuture<Void> skuSaleFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<ItemSaleVo>> saleResponseVo = this.smsClient.querySalesBySkuId(skuId);
            List<ItemSaleVo> itemSaleVoList = saleResponseVo.getData();
            itemVo.setSales(itemSaleVoList);
        }, threadPoolExecutor);

        //库存信息
        CompletableFuture<Void> wareFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.getWareSkuBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntityList = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntityList)) {
                //如果能在集合中任意匹配到其中一个仓库的 存货-锁定 > 0 就为true
                itemVo.setStore(wareSkuEntityList.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
        }, threadPoolExecutor);

        //所有销售属性
        CompletableFuture<Void> allSaleFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<SaleAttrValueVo>> listResponseVo = this.pmsClient.querySaleAttrsBySpuId(skuEntity.getSpuId());
            List<SaleAttrValueVo> attrValueVoList = listResponseVo.getData();
            itemVo.setSaleAttrs(attrValueVoList);
        }, threadPoolExecutor);

        //当前sku的销售属性
        //{4:'暗夜黑',5:'麒麟',6:'128G'}
        CompletableFuture<Void> attrFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<SkuAttrValueEntity>> skuAttrResponseVo = this.pmsClient.querysaleAttrValuesBySkuId(skuId);
            List<SkuAttrValueEntity> attrValueEntityList = skuAttrResponseVo.getData();
            if (!CollectionUtils.isEmpty(attrValueEntityList)) {
                Map<Long, String> map = attrValueEntityList.stream().collect(Collectors.toMap(SkuAttrValueEntity::getAttrId, SkuAttrValueEntity::getAttrValue));
                itemVo.setSaleAttr(map);
            }
        }, threadPoolExecutor);

        //skuId和属性组合的映射关系
        CompletableFuture<Void> mappingFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<String> mappingResponseVo = this.pmsClient.querySaleAttrsMappingSkuIdBySpuId(skuEntity.getSpuId());
            String json = mappingResponseVo.getData();
            itemVo.setSkuJsons(json);
        }, threadPoolExecutor);

        //海报信息
        CompletableFuture<Void> spuDescFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
            SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
            if (spuDescEntity != null && StringUtils.isNotBlank(spuDescEntity.getDecript())) {
                itemVo.setSpuImages(Arrays.asList(StringUtils.split(spuDescEntity.getDecript(), ",")));
            }
        }, threadPoolExecutor);

        //分组信息
        CompletableFuture<Void> groupFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<ItemGroupVo>> groupResponseVo = this.pmsClient.queryGroupsWithAttrValuesBycid(skuEntity.getCategoryId(), skuEntity.getSpuId(), skuId);
            List<ItemGroupVo> groupVoList = groupResponseVo.getData();
            itemVo.setGroups(groupVoList);
        }, threadPoolExecutor);

        //等待所有子任务执行完成才能返回
        CompletableFuture.allOf(catesFuture, groupFuture, spuDescFuture, mappingFuture, attrFuture
                , allSaleFuture, wareFuture, skuSaleFuture, spuImagesFuture, spuFuture, brandFuture).join();

        return itemVo;
    }


    // 生成静态页面的方法
    public void generateHtml(ItemVo itemVo) {

        //给上下文对象设置参数；前端页面渲染需要的属性名为“itemVo”,通过该对象给模板传递渲染所需要的数据
        Context context = new Context();
        context.setVariable("itemVo", itemVo);

        PrintWriter printWriter = null;
        try {
            //初始化文件流
            printWriter = new PrintWriter("E:\\Users\\html\\" + itemVo.getSkuId() + ".html");

            //通过模板引擎生产静态页面 1-模板对象 2-上下文参数 3-生成的页面保存的位置的流
            this.templateEngine.process("item", context, printWriter);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (printWriter != null) {
                printWriter.close();
            }
        }
    }

}


//异步编排技术：将大量的同步方法，编排成异步方法。提升查询效率
class CompletableFutureDemo {
    public static void main(String[] args) {
        //runAsync() 没有返回值
//        CompletableFuture.runAsync(()->{
//            System.out.println("hello completablefuture");
//        });


        //supplyAsync 支持返回值
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            System.out.println("hello completablefuture");
            return "123";
        }).thenApplyAsync(t -> {
            System.out.println("********thenApplyAsync1*******");
            System.out.println("上一个任务的反之结果：" + t);
            return "串行化方法applyAsync1";
        }).thenApplyAsync(t -> {
            System.out.println("********thenApplyAsync1*******");
            System.out.println("上一个任务的反之结果：" + t);
            return "串行化方法applyAsync1";
        });

//                .whenCompleteAsync((t,u)->{//重新从线程池拿一个线程执行此任务。
//            System.out.println("上一个任务的返回结果集t:"+t);
//            System.out.println("上一个任务的异常信息:"+u);
//            System.out.println("执行另一个任务");
//        }).exceptionally(t->{
//            System.out.println("上一个任务的异常信息"+t);
//            System.out.println("异常后的处理任务");
//            return "helllo exceptionally";
//        });


        try {
            //以阻塞的方式获取子任务的返回结果集；不推荐使用；推荐使用计算完成时回调方法。
//            System.out.println(future.get());
            System.out.println("这是主方法的打印");
        } catch (Exception e) {
            //捕获子任务的异常信息
            e.printStackTrace();
        }
    }


}
