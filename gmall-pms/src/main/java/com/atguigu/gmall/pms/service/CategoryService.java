package com.atguigu.gmall.pms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.pms.entity.CategoryEntity;

import java.util.List;
import java.util.Map;

/**
 * 商品三级分类
 *
 * @author ahua
 * @email ahua@atguigu.com
 * @date 2021-01-18 21:05:18
 */
public interface CategoryService extends IService<CategoryEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    List<CategoryEntity> queryCategory(long parentId);

    List<CategoryEntity> queryLv12CatesWithSubsByPid(Long pid);

    List<CategoryEntity> query123CategoriesBycid3(Long cid);
}

