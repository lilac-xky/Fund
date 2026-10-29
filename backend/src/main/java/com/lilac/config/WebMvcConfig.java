package com.lilac.config;

import com.lilac.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置，把登录拦截器挂到业务接口上，登录接口与静态资源放行
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 注入登录拦截器
     *
     * @param authInterceptor 登录拦截器
     */
    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * 注册拦截器，除登录与静态资源外的全部路径都要会话
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 登录与登出本身不能被拦，否则永远进不去
                        "/auth/login",
                        "/auth/logout",
                        "/auth/status",
                        // 静态资源与前端入口
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.ico",
                        "/sw.js",
                        // 错误页重定向
                        "/error"
                );
    }
}
