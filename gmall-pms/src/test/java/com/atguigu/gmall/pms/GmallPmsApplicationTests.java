package com.atguigu.gmall.pms;

import com.atguigu.gmall.pms.mapper.AttrGroupMapper;
import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.service.AttrGroupService;
import com.atguigu.gmall.pms.service.SkuAttrValueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

@SpringBootTest
class GmallPmsApplicationTests {

    @Autowired
    private SkuAttrValueService skuAttrValueService;

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Autowired
    private AttrGroupService attrGroupService;

    @Test
    void contextLoads() {

//        System.out.println(this.skuAttrValueService.querySaleAttrsBySpuId(7l));
//        System.out.println(this.skuAttrValueService.querySaleAttrsMappingSkuIdBySpuId(7l));
//        System.out.println(attrGroupService.queryGroupsWithAttrsBycid(225l, 7l, 7l));
    }


}
