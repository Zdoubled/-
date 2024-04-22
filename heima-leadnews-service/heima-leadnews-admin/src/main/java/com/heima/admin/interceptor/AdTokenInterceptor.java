package com.heima.admin.interceptor;

import com.heima.model.admin.pojos.AdUser;
import com.heima.utils.thread.AdThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

@Slf4j
public class AdTokenInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userId = request.getHeader("userId");
        Optional<String> optional = Optional.ofNullable(userId);
        if (optional.isPresent()) {
            log.info("当前管理员用户userId:{}", userId);
            AdUser adUser = new AdUser();
            adUser.setId(Integer.valueOf(userId));
            AdThreadLocalUtil.setUser(adUser);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        log.info("清理管理员用户userId");
        AdThreadLocalUtil.clear();
    }
}
