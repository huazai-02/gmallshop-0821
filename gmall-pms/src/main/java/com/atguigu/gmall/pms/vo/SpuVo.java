package com.atguigu.gmall.pms.vo;

import com.atguigu.gmall.pms.entity.SpuEntity;
import lombok.Data;

import java.util.List;

@Data
public class SpuVo extends SpuEntity {

    // 图片信息
    private List<String> spuImages;
    // 基础信息
    private List<SpuAttrValueVo> baseAttrs;
    // 库存信息
    private List<SkuVo>  skus;
}
