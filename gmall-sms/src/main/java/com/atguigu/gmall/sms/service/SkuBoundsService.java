package com.atguigu.gmall.sms.service;

import com.atguigu.gmall.sms.api.vo.ItemSaleVo;
import com.atguigu.gmall.sms.api.vo.SkuSaleVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.sms.entity.SkuBoundsEntity;

import java.util.List;

/**
 * 商品spu积分设置
 *
 * @author ahua
 * @email ahua@atguigu.com
 * @date 2021-01-18 22:57:52
 */
public interface SkuBoundsService extends IService<SkuBoundsEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    void saveSales(SkuSaleVo saleVo);

    List<ItemSaleVo> querySalesBySkuId(Long skuId);
}

