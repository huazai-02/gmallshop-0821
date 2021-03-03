package com.atguigu.gmall.search.pojo;


import lombok.Data;

import java.util.List;


/**
 * search.jd.com/Search?keyword=手机 & brandId=1,2,3 &categoryId=225 & props=4:4G-8G & props=5:128G-256G-512G
 * &priceFrom=1000 & priceTo=3000 & store=true & pageNum=1
 *
 */
@Data
public class SearchParamVo {

    //检索关键字
    private String keyword;

    //品牌的过滤条件
    private List<Long> brandId;

    //分类的过滤条件
    private List<Long> categoryId;

    //规格参数的过滤条件 ["4:4G-8G“,”5:128G-256G-512G“]
    private List<String> props;

    //排序字段 0-默认，根据得分降序排列；1-价格降序；2-价格升序；3-销量降序；4-新品降序
    private Integer sort=0;

    //价格区间过滤
    private Double priceFrom;
    private Double priceTo;

    //是否有货的过滤
    private Boolean store;

    //分页参数
    private Integer pageNum=1;
    private final Integer pageSize = 20; //不让用户自定义每页的数据；防止页面不完整，防止爬虫程序。
}
