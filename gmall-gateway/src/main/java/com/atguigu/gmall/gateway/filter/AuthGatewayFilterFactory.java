package com.atguigu.gmall.gateway.filter;


import com.atguigu.gmall.common.utils.IpUtil;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import com.atguigu.gmall.gateway.config.JwtProperties;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

// 要自定义一个局部过滤器，可以实现 GatewayFilterFactory 顶级接口。
//                      可以继承 AbstractGatewayFilterFactory<C> 抽象类
//                      也可以继承抽象类下的具体的类。

@EnableConfigurationProperties(JwtProperties.class)
@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.PathConfig> {

    @Autowired
    private JwtProperties properties;


    // 重写父类构造方法，指定接收的参数
    public AuthGatewayFilterFactory() {
        super(PathConfig.class);
    }

    // 指定字段顺序
    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("paths");
    }

    //如果要接收的参数是一个集合，需要额外实现字段类型方法
    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    @Override
    public GatewayFilter apply(PathConfig config) {

        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                System.out.println("这是局部过滤器，只拦截特定路由对应的服务："+config.paths);
               // 1. 判断请求是否在拦截路径内，不在则直接放行
                List<String> paths = config.paths;//拦截名单
                // 获取网关的request和response对象
                ServerHttpRequest request = exchange.getRequest();
                ServerHttpResponse response = exchange.getResponse();
                String curPath = request.getURI().getPath();//当前请求的路劲
                if (CollectionUtils.isEmpty(paths) || !paths.stream().anyMatch(path-> StringUtils.startsWith(curPath,path))){
                   return chain.filter(exchange);
                }

                //2. 获取请求中的token，异步：头信息；同步：cookeie
                String token = request.getHeaders().getFirst("token");
                if (StringUtils.isBlank(token)){
                    MultiValueMap<String, HttpCookie> cookies = request.getCookies();
                    if (!CollectionUtils.isEmpty(cookies) && cookies.containsKey(properties.getCookieName())){
                        token = cookies.getFirst(properties.getCookieName()).getValue();
                    }
                }

                //3. 判断token信息是否为空，为空则重定向到登录界面
                if (StringUtils.isBlank(token)){
                    response.setStatusCode(HttpStatus.SEE_OTHER);//303 重定向
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl="+request.getURI());
                    return response.setComplete(); //拦截请求
                }

                try {
                    //4. 使用公钥解析jwt。解析异常则重定向到登录界面
                    Map<String, Object> map = JwtUtils.getInfoFromToken(token, properties.getPublicKey());
                    //5. 判断是否自己的token，获取当前用户的ip地址和载荷中的ip地址是否一致。一致则放行，否则重定向到登录界面
                    String ip = map.get("ip").toString();
                    String curIp = IpUtil.getIpAddressAtGateway(request);
                    if (!StringUtils.equals(ip,curIp)){
                        response.setStatusCode(HttpStatus.SEE_OTHER);//303 重定向
                        response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl="+request.getURI());
                        return response.setComplete(); //拦截请求
                    }
                    //6. 把jwt中的登录信息传递给后续服务，通过request头传递登录信息;
                    // 要重新构建request请求头的信息需要先用 mutate() 方法进行转换
                    request.mutate().header("userId", map.get("userId").toString()).build();
                    System.out.println(map.get("userId"));
                    exchange.mutate().request(request).build();//传递信息的交换机也要转换。
                    //7. 放行
                    return chain.filter(exchange);
                } catch (Exception e) {
                    e.printStackTrace();
                    response.setStatusCode(HttpStatus.SEE_OTHER);//303 重定向
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl="+request.getURI());
                    return response.setComplete(); //拦截请求
                }
            }
        };
    }


    @Data
    public static class PathConfig{
        private List<String> paths;
    }
}
