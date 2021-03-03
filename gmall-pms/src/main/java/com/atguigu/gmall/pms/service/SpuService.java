package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.vo.SpuVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.pms.entity.SpuEntity;

/**
 * spu信息
 *
 * @author ahua
 * @email ahua@atguigu.com
 * @date 2021-01-18 21:05:18
 */
public interface SpuService extends IService<SpuEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    PageResultVo getSpuInfo(long categoryId, PageParamVo paramVo);

    void bigSave(SpuVo spuVo);
}

