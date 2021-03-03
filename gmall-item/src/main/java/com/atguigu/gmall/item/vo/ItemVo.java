package com.atguigu.gmall.item.vo;

import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.api.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ItemVo {

    //面包屑:三级分类
    private List<CategoryEntity> categories;
    //面包屑:品牌
    private Long brandId;
    private String brandName;
    //面包屑:spu
    private Long spuId;
    private String spuName;


    //中间的sku信息
    private Long skuId;
    private String title;
    private String subTitle;
    private BigDecimal price;
    private String defaultImage;
    private Integer weight;


    //图片列表
    private List<SkuImagesEntity> images;

    //营销信息
    private List<ItemSaleVo> sales;

    //库存信息
    private Boolean store=false;

    //[{attrId:4,attrName:颜色,attrValues:['暗夜黑','白天白']},
    // {attrId:5,attrName:CPU品牌,attrValues:['麒麟,'骁龙'}],
    // {attrId:6,attrName:存储,attrValues:['128G,'256G'}]]
    //跟当前sku相同的spu下的所有sku的销售属性列表
    private List<SaleAttrValueVo> saleAttrs;

    //当前sku的销售参数
    //{4:'暗夜黑',5:'麒麟',6:'128G'}
    private Map<Long,String> saleAttr;

    //销售属性组合和skuId的映射关系
    //{'暗夜黑,8G,128G':10,'白天白,12G,256G':11}
    private String skuJsons;


    //商品的海报信息
    private List<String> spuImages;

    //规格参数分组列表
    private List<ItemGroupVo> groups;



}
