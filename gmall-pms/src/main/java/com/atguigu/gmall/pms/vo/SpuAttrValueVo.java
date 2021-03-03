package com.atguigu.gmall.pms.vo;

import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.List;


public class SpuAttrValueVo extends SpuAttrValueEntity {


    //重写 valueSelected 的setter方法
    public void setValueSelected(List<String> valueSelected) {
        //如果为空则不设置
        if (CollectionUtils.isEmpty(valueSelected)){
            return;
        }

        this.setAttrValue(StringUtils.join(valueSelected,","));

    }
}
