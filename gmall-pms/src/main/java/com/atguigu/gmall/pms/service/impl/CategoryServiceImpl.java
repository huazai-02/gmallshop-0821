package com.atguigu.gmall.pms.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.CategoryMapper;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.service.CategoryService;

import javax.annotation.Resource;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, CategoryEntity> implements CategoryService {

    @Resource
    private CategoryMapper categoryMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<CategoryEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<CategoryEntity> queryCategory(long parentId) {

        QueryWrapper<CategoryEntity> queryWrapper = new QueryWrapper<>();
        if (parentId!=-1){
            queryWrapper.eq("parent_id", parentId);
        }
        List<CategoryEntity> categoryEntities = this.list(queryWrapper);
        return categoryEntities;
    }

    @Override
    public List<CategoryEntity> queryLv12CatesWithSubsByPid(Long pid) {
        return this.categoryMapper.queryLv12CatesWithSubsByPid(pid);

    }

    @Override
    public List<CategoryEntity> query123CategoriesBycid3(Long cid) {

        //根据三级分类的ID查询三级分类
        CategoryEntity categoryEntity3 = this.getById(cid);
        if(categoryEntity3==null){
            return null;
        }

        //根据二级分类的ID查询二级分类
        CategoryEntity categoryEntity2 = this.getById(categoryEntity3.getParentId());


        //根据一级分类的ID查询一级分类
        CategoryEntity categoryEntity1 = this.getById(categoryEntity2.getParentId());
        return Arrays.asList(categoryEntity1,categoryEntity2,categoryEntity3);
    }

}