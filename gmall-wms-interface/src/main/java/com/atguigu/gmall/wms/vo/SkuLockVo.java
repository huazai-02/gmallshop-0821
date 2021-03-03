package com.atguigu.gmall.wms.vo;

import lombok.Data;

@Data
public class SkuLockVo {

    private Long skuId;
    private Integer count;

    private Long wareSkuId;//锁定仓库的ID
    private Boolean lock;//锁定状态
}
