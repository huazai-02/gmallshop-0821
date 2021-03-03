package com.atguigu.gmall.cart.interceptor;

import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties properties;

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("这是拦截器的前置方法");

        UserInfo userInfo = new UserInfo();

        // 获取userKey
        String userKey = CookieUtils.getCookieValue(request, properties.getUserKey());
        if (StringUtils.isBlank(userKey)){//如果userkey为空，就制作一个放入cookie中
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request,response,properties.getUserKey(),userKey,properties.getExpire());
        }
        userInfo.setUserKey(userKey);
        // 获取token
        String token = CookieUtils.getCookieValue(request, properties.getCookieName());
        if (StringUtils.isNotBlank(token)){
            Map<String, Object> map = JwtUtils.getInfoFromToken(token, properties.getPublicKey());
            userInfo.setUserId(Long.valueOf(map.get("userId").toString()));
        }

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
