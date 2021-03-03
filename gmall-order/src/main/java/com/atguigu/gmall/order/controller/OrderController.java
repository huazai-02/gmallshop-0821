package com.atguigu.gmall.order.controller;


import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.order.pojo.OrderConfirmVo;
import com.atguigu.gmall.oms.pojo.OrderSubmitVo;
import com.atguigu.gmall.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class OrderController {

    @Autowired
    private OrderService orderService;

    //在购物车选中的商品都已经保存在购物车的数据库中；所以不需要传递任何参数；通过远程调用cart服务获取即可。
    @GetMapping("confirm")
    public String confirm(Model model){
        OrderConfirmVo confirmVo = this.orderService.confirm();
        System.out.println(confirmVo);
        model.addAttribute("confirmVo", confirmVo);
        return "trade";
    }


    @PostMapping("submit")
    @ResponseBody
    public ResponseVo<String> submit(@RequestBody OrderSubmitVo submitVo){
        this.orderService.submit(submitVo);
        return ResponseVo.ok(submitVo.getOrderToken());
    }

}
