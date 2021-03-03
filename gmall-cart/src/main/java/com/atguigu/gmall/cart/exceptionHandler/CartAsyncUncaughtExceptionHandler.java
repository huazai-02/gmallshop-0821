package com.atguigu.gmall.cart.exceptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Component
@Slf4j
public class CartAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {


    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String EXCEPTION_KEY = "cart:exception:info";

    // 通过 统一异常处理 可获取到对应的失败的 方法和参数
    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... objects) {
        log.error("异步任务出现异常了,方法：{}，参数{}，异常：{}", method, Arrays.asList(objects),throwable.getMessage());

        //通过统一异常处理监控写入到mysql失败的数据。
        //当写入mysql失败时，以 redis<'cart:exception:info', 用户IDset[1,2,3,4,5]>记录
        //再去定义定时任务，定时同步数据
        BoundSetOperations<String, String> setOps = this.redisTemplate.boundSetOps(EXCEPTION_KEY);
        setOps.add(objects[0].toString());


    }
}
