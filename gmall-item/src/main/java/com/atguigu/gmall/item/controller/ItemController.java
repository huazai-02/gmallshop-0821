package com.atguigu.gmall.item.controller;

import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.item.vo.ItemVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.concurrent.ThreadPoolExecutor;

@Controller
public class ItemController {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @GetMapping("{skuId}.html")
    public String toItem(@PathVariable("skuId")Long skuId, Model model){
        ItemVo itemVo = this.itemService.loadData(skuId);
        System.out.println(itemVo);

        model.addAttribute("itemVo", itemVo);

        //通过异步的方式来进行页面静态化 避免阻塞查询时页面的返回。
        threadPoolExecutor.execute(()->{
            //页面静态化
            itemService.generateHtml(itemVo);
        });

        return "item";
    }
}
