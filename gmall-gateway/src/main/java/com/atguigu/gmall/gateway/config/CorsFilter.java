package com.atguigu.gmall.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;


// 配置全局跨域
@Configuration
public class CorsFilter {

    @Bean
    public CorsWebFilter corsWebFilter(){
        //初始化cors配置对象
        CorsConfiguration config = new CorsConfiguration();

        //允许的域，不能写* 写*会导致cookie无法使用
        config.addAllowedOrigin("http://manager.gmall.com");
        config.addAllowedOrigin("http://localhost:1000");
        config.addAllowedOrigin("http://gmall.com");
        config.addAllowedOrigin("http://www.gmall.com");
        //允许的请求方式
        config.addAllowedMethod("*");
        //允许的头信息
        config.addAllowedHeader("*");
        //是否允许携带cookie信息
        config.setAllowCredentials(true);

        //拦截所有请求，过滤是否可跨域
        UrlBasedCorsConfigurationSource corsConfigurationSource = new UrlBasedCorsConfigurationSource();
        corsConfigurationSource.registerCorsConfiguration("/**",config);
        return new  CorsWebFilter(corsConfigurationSource);
    }
}
