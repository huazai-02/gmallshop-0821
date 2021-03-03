package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    //浏览器访问服务可以通过cookie传递token,从而解析得到userId,但这里是通过订单服务来访问购物车，没有cookie；然而能够访问到订单服务
    //必然是登录状态的，否则会被网关拦截。因此也能获取到userId，并由此传递给此方法。
    @GetMapping("user/{userId}")
    @ResponseBody
    public ResponseVo<List<Cart>> queryCheckedCarts(@PathVariable("userId")Long userId){
        List<Cart> carts = this.cartService.queryCheckedCarts(userId);
        return ResponseVo.ok(carts);
    }



    //添加商品到购物车
    @GetMapping
    public String saveCart(Cart cart) {
        this.cartService.savaCart(cart);
        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId();
    }


    //加入购物车后的成果回显
    @GetMapping("addCart.html")
    public String toCart(@RequestParam("skuId") Long skuId, Model model) {
        Cart cart = this.cartService.queryCartBySkuId(skuId);
        model.addAttribute("cart", cart);
        return "addCart";
    }

    //查询购物车
    @GetMapping("cart.html")
    public String queryCarts(Model model){
        List<Cart> carts = this.cartService.queryCarts();
        model.addAttribute("carts", carts);
        return "cart";
    }

    //更新购物车的数量
    @PostMapping("updateNum")
    @ResponseBody
    public ResponseVo updateNum(@RequestBody Cart cart){
        this.cartService.updateNum(cart);
        return ResponseVo.ok();
    }

    //删除购物车
    @PostMapping("deleteCart")
    @ResponseBody
    public ResponseVo deleteCart(@RequestParam("skuId")Long skuId){
        this.cartService.deleteCart(skuId);
        return ResponseVo.ok();
    }






    //测试声明式异步
    @RequestMapping("test")
    @ResponseBody
    public String test() {
        long now = System.currentTimeMillis();
        this.cartService.executor1();
        this.cartService.executor2();
//        try {
//            System.out.println(future1.get());
//            System.out.println(future2.get());
//        } catch (Exception e) {
//            System.out.println(e.getMessage());
//        }

//        future1.addCallback(result -> {
//            System.out.println(result);
//        }, ex -> {
//            System.out.println(ex.getMessage());
//        });
//        future2.addCallback(result -> {
//            System.out.println(result);
//        }, ex -> {
//            System.out.println(ex.getMessage());
//        });

        System.out.println("controller方法执行的时间" + (System.currentTimeMillis() - now));
        return "hello test";
    }
}

