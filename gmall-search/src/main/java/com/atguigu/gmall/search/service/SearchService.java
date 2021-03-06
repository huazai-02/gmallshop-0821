package com.atguigu.gmall.search.service;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryBrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public SearchResponseVo search(SearchParamVo paramVo) {

        try {
            SearchRequest searchRequest = new SearchRequest(new String[]{"goods"}, this.builderDsl(paramVo));
            SearchResponse response = this.restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            SearchResponseVo responseVo = this.parseResult(response);
            //分页参数只有在搜索参数中才有
            responseVo.setPageSize(paramVo.getPageSize());
            responseVo.setPageNum(paramVo.getPageNum());
            return responseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    //将es响应结果集response解析成SearchResponseVo对象返回前端。
    private SearchResponseVo parseResult(SearchResponse response){
        SearchResponseVo responseVo = new SearchResponseVo();

        //1.解析hits，获取到总记录数和当前页的记录列表
        SearchHits hits = response.getHits();

        //当前页的数据
        SearchHit[] hitsHits = hits.getHits();
        List<Goods> goodsList = Stream.of(hitsHits).map(hitsHit ->{
            String json = hitsHit.getSourceAsString();
            Goods goods = JSON.parseObject(json, Goods.class);
            //获取高亮标题覆盖掉_source中的普通title
            Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            goods.setTitle(highlightField.getFragments()[0].string());
            return goods;
        }).collect(Collectors.toList());

        responseVo.setGoodsList(goodsList);

        //总记录数
        responseVo.setTotal(hits.totalHits);


        //2.解析aggregations，获取到品牌列表、分类列表以及规格参数列表
        // 把聚合结果集以Map的形式解析：key-聚合名称 value-聚合的内容
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        //2.1 获取分类
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms)aggregationMap.get("categoryIdAgg");
        List<? extends Terms.Bucket> buckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            // 把每个桶转化成每个分类
            List<CategoryEntity> categoryEntities = buckets.stream().map(bucket->{
                CategoryEntity categoryEntity = new CategoryEntity();
                categoryEntity.setId(((Terms.Bucket)bucket).getKeyAsNumber().longValue());
                // 获取分类名称的子聚合获取分类名称
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms)((Terms.Bucket) bucket).getAggregations().get("categoryNameAgg");
                categoryEntity.setName(categoryNameAgg.getBuckets().get(0).getKeyAsString());

                return categoryEntity;
            }).collect(Collectors.toList());
            responseVo.setCategories(categoryEntities);
        }



        //2.2 获取品牌
        ParsedLongTerms brandIdAgg =(ParsedLongTerms)aggregationMap.get("brandIdAgg");
        List<? extends Terms.Bucket> brandIdAggBuckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(brandIdAggBuckets)){
            responseVo.setBrands(brandIdAggBuckets.stream().map(bucket->{
                BrandEntity brandEntity = new BrandEntity();

                // 外层同的Id就是品牌的id
                brandEntity.setId(bucket.getKeyAsNumber().longValue());

                //获取到桶中的子聚合：名称和logo的聚合
                Map<String, Aggregation> brandSubAggMap = ((Terms.Bucket)bucket).getAggregations().asMap();
                ParsedStringTerms brandNameAgg = (ParsedStringTerms)brandSubAggMap.get("brandNameAgg");
                // 每个品牌名称子聚合中有且仅有一个桶
                brandEntity.setName(brandNameAgg.getBuckets().get(0).getKeyAsString());

                // 获取logo的子聚合，每个Logo子聚合有且仅有一个桶
                ParsedStringTerms logoAgg = (ParsedStringTerms)brandSubAggMap.get("logoAgg");
                brandEntity.setLogo(logoAgg.getBuckets().get(0).getKeyAsString());

                return brandEntity;
            }).collect(Collectors.toList()));
        }


        //2.3 获取规格参数
        //获取到规格参数的嵌套聚合
        ParsedNested attrAgg = (ParsedNested)aggregationMap.get("attrAgg");
        //获取嵌套聚合中attrId的聚合
        ParsedLongTerms attrIdAgg =(ParsedLongTerms) attrAgg.getAggregations().get("attrIdAgg");
        //获取attrId聚合中的桶集合,获取所有的检索类型的规格参数
        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
        //有些商品或者有些关键字可能没有检索类型的规格参数
        if (!CollectionUtils.isEmpty(attrIdAggBuckets)){
            //把attrId聚合中的桶集合转化成List<SearchResponseAttrVo>
            List<SearchResponseAttrVo> searchResponseAttrVos = attrIdAggBuckets.stream().map(bucket->{
                //把每个桶转化成SearchResponseAttrVo对象
                SearchResponseAttrVo responseAttrVo = new SearchResponseAttrVo();
                //桶中的key就是attrId
                responseAttrVo.setAttrId(((Terms.Bucket)bucket).getKeyAsNumber().longValue());
                // 获取子聚合获取attrName和attrValues
                Map<String, Aggregation> subAggMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                // 获取attrName的子聚合
                ParsedStringTerms attrNameAgg = (ParsedStringTerms)subAggMap.get("attrNameAgg");
                responseAttrVo.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());
                // 获取attrValues的子聚合
                ParsedStringTerms attrValueAgg = (ParsedStringTerms) subAggMap.get("attrValueAgg");
                List<? extends Terms.Bucket> valueBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(valueBuckets)){
                    List<String> attrValues = valueBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                    responseAttrVo.setAttrValues(attrValues);
                }
                return responseAttrVo;
            }).collect(Collectors.toList());
            responseVo.setFilters(searchResponseAttrVos);
        }
        return responseVo;
    }

    //构建DSL查询语句
    private SearchSourceBuilder builderDsl(SearchParamVo paramVo) {

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        String keyword = paramVo.getKeyword();
        if (StringUtils.isBlank(keyword)) {
            //TODO:可以打广告
            return sourceBuilder;
        }
        //1.构建检索条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        //1.1 构建匹配搜索条件
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));
        //1.2 构建过滤条件
        //1.2.1 构建品牌的过滤
        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        }

        //1.2.2 构建分类的过滤
        List<Long> categoryId = paramVo.getCategoryId();
        if (!CollectionUtils.isEmpty(categoryId)) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", categoryId));
        }

        //1.2.3 构建价格区间的过滤
        Double priceFrom = paramVo.getPriceFrom();//起始价格
        Double priceTo = paramVo.getPriceTo();//终止价格
        if (priceFrom != null || priceTo != null) {//有一个不为空才需要添加价格区间的过滤
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (priceFrom != null) {
                rangeQuery.gte(priceFrom);
            }
            if (priceTo != null) {
                rangeQuery.lte(priceTo);
            }
            boolQueryBuilder.filter(rangeQuery);
        }

        //1.2.4 构建是否有货的过滤
        Boolean store = paramVo.getStore();
        if (store!=null && store) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("store", store));
        }

        //1.2.5 构建规格参数的嵌套过滤
        //["4:4G-8G“,”5:128G-256G-512G“]
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)) {
            props.forEach(prop -> {//每一个prop: 4:8G-12G

                //用冒号分割出attrId和attrValue字符串
                String[] attr = StringUtils.split(prop, ":");
                if (attr!=null&&attr.length==2){

                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    //分割后的第一位就是attrId
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", attr[0]));
                    
                    //分割后的第二位：4:8G-12G
                    String[] attrValues = StringUtils.split(attr[1], "-");
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue", attrValues));

                    //每一个prop对应一个嵌套过滤：1-对应嵌套过滤中的path 2-嵌套过滤中的query 3-得分模式
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));
                }

            });
        }

        //2.排序
        Integer sort = paramVo.getSort();
        switch (sort){
            case 1:sourceBuilder.sort("price", SortOrder.DESC); break;
            case 2:sourceBuilder.sort("price", SortOrder.ASC); break;
            case 3:sourceBuilder.sort("sales", SortOrder.DESC); break;
            case 4:sourceBuilder.sort("createTime", SortOrder.DESC); break;
            default:
                sourceBuilder.sort("_score",SortOrder.DESC); break;
        }


        //3.构建分页条件
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum-1)*pageSize);
        sourceBuilder.size(pageSize);

        //4.构建高亮
        sourceBuilder.highlighter(
                new HighlightBuilder()
                        .field("title")
                        .preTags("<font style='color:red;'>")
                        .postTags("</font>"));

        //5.构建聚合
        //5.1 构建品牌的聚合
        sourceBuilder.aggregation(
                AggregationBuilders.terms("brandIdAgg").field("brandId")
                        .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                        .subAggregation(AggregationBuilders.terms("logoAgg").field("logo")));

        //5.2 构建分类的聚合
        sourceBuilder.aggregation(
                AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                        .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));

        //5.3 构建规格参数的聚合
        sourceBuilder.aggregation(
                AggregationBuilders.nested("attrAgg", "searchAttrs")
                        .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                                .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                                .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))));

        //6.构建结果集过滤
        sourceBuilder.fetchSource(new String[]{"skuId","defaultImage","price","title","subTitle"},null);
//        System.out.println(sourceBuilder);
        return sourceBuilder;
    }
}
