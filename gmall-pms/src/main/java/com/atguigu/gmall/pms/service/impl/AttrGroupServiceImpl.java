package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.mapper.SpuAttrValueMapper;
import com.atguigu.gmall.pms.service.AttrService;
import com.atguigu.gmall.pms.vo.AttrValueVo;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.AttrGroupMapper;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.gmall.pms.service.AttrGroupService;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupMapper, AttrGroupEntity> implements AttrGroupService {

    @Autowired
    AttrService attrService;

    @Autowired
    private AttrMapper attrMapper;

    @Autowired
    private SkuAttrValueMapper skuattrValueMapper;

    @Autowired
    private SpuAttrValueMapper spuAttrValueMapper;


    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<AttrGroupEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageResultVo(page);
    }



    @Override
    public List<AttrGroupEntity> queryAttrGroupWithAttrs(long catId) {
        //根据分类id查询参数分组以及相应的参数

        // 根据分类id查询分组
        QueryWrapper<AttrGroupEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category_id", catId);
        List<AttrGroupEntity> groupEntities = this.list(queryWrapper);

        if (CollectionUtils.isEmpty(groupEntities)){
            return null;
        }
        //根据分组ID查询规格参数
        for (AttrGroupEntity groupEntity : groupEntities) {
            Long groupId = groupEntity.getId();
            QueryWrapper<AttrEntity> queryWrapper1 = new QueryWrapper<>();
            queryWrapper1.eq("group_id", groupId).eq("type", 1);
            List<AttrEntity> attrEntityList = attrService.list(queryWrapper1);
            groupEntity.setAttrEntities(attrEntityList);
        }
        return groupEntities;

        //方式2：
//        groupEntities.forEach(group->{
//            List<AttrEntity> attrEntities = attrService.list(new QueryWrapper<AttrEntity>().eq("group_id",group.getId()));
//            group.setAttrEntities(attrEntities);
//        });
//        return groupEntities;
    }

    @Override
    public List<ItemGroupVo> queryGroupsWithAttrsBycid(Long cid, Long spuId, Long skuId) {
        //根据分类ID查询出所有的分组信息
        List<AttrGroupEntity> groupEntities = this.list(new QueryWrapper<AttrGroupEntity>().eq("category_id", cid));
        if (CollectionUtils.isEmpty(groupEntities)){
            return null;
        }
       return groupEntities.stream().map(groupEntity->{
            ItemGroupVo groupVo = new ItemGroupVo();
            //获取每个分组下的规格列表
            List<AttrEntity> attrEntityList = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("group_id", groupEntity.getId()));
            if (!CollectionUtils.isEmpty(attrEntityList)){

                //获取attrId的集合
                List<Long> attrIds = attrEntityList.stream().map(AttrEntity::getId).collect(Collectors.toList());

                List<AttrValueVo> attrValueVos = new ArrayList<>();
                //查询销售规格参数及值
                List<SkuAttrValueEntity> skuAttrValueEntities = this.skuattrValueMapper.selectList(new QueryWrapper<SkuAttrValueEntity>().eq("sku_id", skuId).in("attr_id", attrIds));
                if (!CollectionUtils.isEmpty(skuAttrValueEntities)){
                    attrValueVos.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                        AttrValueVo attrValueVo = new AttrValueVo();
                        BeanUtils.copyProperties(skuAttrValueEntity, attrValueVo);
                        return attrValueVo;
                    }).collect(Collectors.toList()));
                }

                //查询基本规格参数及值
                List<SpuAttrValueEntity> spuAttrValueEntities = this.spuAttrValueMapper.selectList(new QueryWrapper<SpuAttrValueEntity>().eq("spu_id", spuId).eq("attr_id", attrIds));
                if (!CollectionUtils.isEmpty(spuAttrValueEntities)){
                    attrValueVos.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                        AttrValueVo attrValueVo = new AttrValueVo();
                        BeanUtils.copyProperties(spuAttrValueEntity, attrValueVo);
                        return attrValueVo;
                    }).collect(Collectors.toList()));
                }
                groupVo.setAttrValue(attrValueVos);
            }
            groupVo.setId(groupEntity.getId());
            groupVo.setName(groupEntity.getName());
            return groupVo;
        }).collect(Collectors.toList());
    }
}