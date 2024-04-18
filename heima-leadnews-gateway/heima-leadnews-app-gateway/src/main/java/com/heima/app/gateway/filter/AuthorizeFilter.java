package com.heima.app.gateway.filter;

import com.heima.app.gateway.utils.AppJwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthorizeFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //1.判断是否为登录请求
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        if (request.getURI().getPath().contains("/login")) {
            //是登录请求，直接放行
            return chain.filter(exchange);
        }
        //2.不是登录请求，判断是否有token
        String token = request.getHeaders().getFirst("token");
        if (StringUtils.isBlank(token)) {
            //没有token，返回401
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
        try {
            //3.有token，获取jwt令牌，并判断令牌是否有效
            Claims claimsBody = AppJwtUtil.getClaimsBody(token);
            int result = AppJwtUtil.verifyToken(claimsBody);

            if (result == 1 || result == 2) {
                //令牌已过期
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }
            //获取用户信息
            Object userId = claimsBody.get("id");
            //存储在请求头中
            request.mutate().headers(httpHeaders -> httpHeaders.add("userId",userId+""));
            //重置请求
            exchange.mutate().request(request);
        }catch (Exception e){
            e.printStackTrace();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        //4.令牌有效，放行
        return chain.filter(exchange);
    }

    /**
     * 设置优先级
     * @return
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
