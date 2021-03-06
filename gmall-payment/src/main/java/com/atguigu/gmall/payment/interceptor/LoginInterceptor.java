package com.atguigu.gmall.payment.interceptor;


import com.atguigu.gmall.payment.config.JwtProperties;
import com.atguigu.gmall.payment.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties properties;

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    // 这里的拦截器只是为了获取请求头内的 userId ；实际上不配置拦截器也可以。每次从request对象获取。
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {


        UserInfo userInfo = new UserInfo();


        // 获取token
        String userId = request.getHeader("userId");
        userInfo.setUserId(Long.valueOf(userId));

        THREAD_LOCAL.set(userInfo);


        return true;//目的是为了获取在网关就解析存放到请求头中的登录信息，不是为了拦截，所以都为true
    }

    // 暴露一个公共的被读取的方法
    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 这里必须清空threadLocal中的资源，因为使用的是tomcat线程池，线程无法结束
        THREAD_LOCAL.remove();
    }
}
