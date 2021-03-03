package com.atguigu.gmall.sms.api.controller;


import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.sms.api.vo.ItemSaleVo;
import com.atguigu.gmall.sms.api.vo.SkuSaleVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface GmallSmsApi {

    @PostMapping("sms/skubounds/save/saleAttrs")
    public ResponseVo<Object> saveSales(@RequestBody SkuSaleVo saleVo);


    @GetMapping("sms/skubounds/sales/{skuId}")
    public ResponseVo<List<ItemSaleVo>> querySalesBySkuId(@PathVariable("skuId")Long skuId);
}