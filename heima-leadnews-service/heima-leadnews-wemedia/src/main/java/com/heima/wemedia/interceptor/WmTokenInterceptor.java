package com.heima.wemedia.interceptor;

import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.thread.WmThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

@Slf4j
public class WmTokenInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取当前用户id
        String userId = request.getHeader("userId");
        Optional<String> optional = Optional.ofNullable(userId);
        //2.非空判断
        if (optional.isPresent()) {
            log.info("当前用户userId:{}", userId);
            WmUser wmUser = new WmUser();
            wmUser.setId(Integer.valueOf(userId));
            //3.把用户id存入上下文中
            WmThreadLocalUtil.setUser(wmUser);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        log.info("清除线程变量中的用户信息");
        WmThreadLocalUtil.clear();
    }
}
