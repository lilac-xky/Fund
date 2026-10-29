package com.lilac.auth;

import com.lilac.enums.HttpsCodeEnum;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器，业务接口须带有效会话，登录接口由配置放行
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthProperties authProperties;

    private final SessionRegistry sessionRegistry;

    /**
     * 注入登录配置与会话管理
     *
     * @param authProperties  登录配置
     * @param sessionRegistry 会话管理
     */
    public AuthInterceptor(AuthProperties authProperties, SessionRegistry sessionRegistry) {
        this.authProperties = authProperties;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * 请求前校验会话，未登录一律返回 401 与统一 Result 结构
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return 放行返回 true
     * @throws Exception 写响应失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!authProperties.isEnabled()) {
            return true;
        }
        if (sessionRegistry.valid(tokenOf(request))) {
            return true;
        }
        // 后端只出 JSON，未登录时不给重定向，由前端据 NEED_LOGIN 自行跳到登录页
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(unauthorizedBody());
        return false;
    }

    /**
     * 从 Cookie 取会话令牌
     *
     * @param request 请求
     * @return 令牌，未带 Cookie 返回 null
     */
    private String tokenOf(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (authProperties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 未登录的返回体，与统一 Result 结构一致
     *
     * @return JSON 文本
     */
    private String unauthorizedBody() {
        return "{\"code\":" + HttpsCodeEnum.NEED_LOGIN.getCode()
                + ",\"msg\":\"" + HttpsCodeEnum.NEED_LOGIN.getMsg() + "\",\"data\":null}";
    }
}
