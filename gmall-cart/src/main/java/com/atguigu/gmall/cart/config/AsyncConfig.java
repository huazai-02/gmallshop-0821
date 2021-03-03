package com.atguigu.gmall.cart.config;

import com.atguigu.gmall.cart.exceptionHandler.CartAsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;

// 把重写的用来处理未被捕获异常的购物车处理器配置到异步配置中。
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Autowired
    private CartAsyncUncaughtExceptionHandler exceptionHandler;

    //可以配置线程池约束线程数
    @Override
    public Executor getAsyncExecutor() {
        return null;
    }


    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return exceptionHandler;
    }
}
