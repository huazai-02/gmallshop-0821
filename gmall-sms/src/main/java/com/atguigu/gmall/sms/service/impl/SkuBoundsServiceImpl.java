package com.atguigu.gmall.sms.service.impl;

import com.atguigu.gmall.sms.api.vo.ItemSaleVo;
import com.atguigu.gmall.sms.api.vo.SkuSaleVo;
import com.atguigu.gmall.sms.entity.SkuFullReductionEntity;
import com.atguigu.gmall.sms.entity.SkuLadderEntity;
import com.atguigu.gmall.sms.mapper.SkuFullReductionMapper;
import com.atguigu.gmall.sms.mapper.SkuLadderMapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.sms.mapper.SkuBoundsMapper;
import com.atguigu.gmall.sms.entity.SkuBoundsEntity;
import com.atguigu.gmall.sms.service.SkuBoundsService;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;


@Service("skuBoundsService")
public class SkuBoundsServiceImpl extends ServiceImpl<SkuBoundsMapper, SkuBoundsEntity> implements SkuBoundsService {

    @Resource
    private SkuFullReductionMapper skuFullReductionMapper;

    @Resource
    private SkuLadderMapper skuLadderMapper;



    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SkuBoundsEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SkuBoundsEntity>()
        );

        return new PageResultVo(page);
    }

    @Transactional
    @Override
    public void saveSales(SkuSaleVo saleVo) {

        //3.1 保存sku_bounds表
        SkuBoundsEntity skuBoundsEntity = new SkuBoundsEntity();
        BeanUtils.copyProperties(saleVo, skuBoundsEntity);
        List<Integer> work = saleVo.getWork();
        if (CollectionUtils.isNotEmpty(work)){
            skuBoundsEntity.setWork(work.get(0)*8+work.get(1)*4+work.get(2)*2+work.get(3));
        }
        this.save(skuBoundsEntity);
        //3.2 保存sku_full_reduction
        SkuFullReductionEntity skuFullReductionEntity = new SkuFullReductionEntity();
        BeanUtils.copyProperties(saleVo, skuFullReductionEntity);
        skuFullReductionEntity.setAddOther(saleVo.getFullAddOther());
        skuFullReductionMapper.insert(skuFullReductionEntity);
        //3.3 保存sku_ladder表
        SkuLadderEntity skuLadderEntity = new SkuLadderEntity();
        BeanUtils.copyProperties(saleVo,skuLadderEntity);
        skuLadderMapper.insert(skuLadderEntity);
    }

    @Override
    public List<ItemSaleVo> querySalesBySkuId(Long skuId) {

        List<ItemSaleVo> itemSaleVos = new ArrayList<>();
        //查询积分营销信息
        SkuBoundsEntity skuBoundsEntity = this.getOne(new QueryWrapper<SkuBoundsEntity>().eq("sku_id", skuId));
        if (skuBoundsEntity!=null){
            ItemSaleVo itemSaleVo = new ItemSaleVo();
            itemSaleVo.setType("积分");
            itemSaleVo.setDesc("送"+skuBoundsEntity.getBuyBounds()+"购物积分"+skuBoundsEntity.getGrowBounds()+"成长积分");
            itemSaleVos.add(itemSaleVo);
        }
        //查询满减营销信息
        SkuFullReductionEntity reductionEntity = this.skuFullReductionMapper.selectOne(new QueryWrapper<SkuFullReductionEntity>().eq("sku_id", skuId));
        if (reductionEntity!=null){
            ItemSaleVo itemSaleVo = new ItemSaleVo();
            itemSaleVo.setType("满减");
            itemSaleVo.setDesc("满"+reductionEntity.getFullPrice()+"减"+reductionEntity.getReducePrice());
            itemSaleVos.add(itemSaleVo);
        }

        //查询打折营销信息
        SkuLadderEntity ladderEntity = this.skuLadderMapper.selectOne(new QueryWrapper<SkuLadderEntity>().eq("sku_id", skuId));
        if (ladderEntity!=null){
            ItemSaleVo itemSaleVo = new ItemSaleVo();
            itemSaleVo.setDesc("满"+ladderEntity.getFullCount()+"件打"+ladderEntity.getDiscount().divide(new BigDecimal(10))+"折");
            itemSaleVo.setType("打折");
            itemSaleVos.add(itemSaleVo);
        }

        return itemSaleVos;
    }

}