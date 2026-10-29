package com.lilac.controller;

import com.lilac.auth.AuthProperties;
import com.lilac.auth.CredentialResolver;
import com.lilac.auth.LoginAttemptGuard;
import com.lilac.auth.SessionRegistry;
import com.lilac.domain.dto.auth.LoginRequest;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.AuthStatusVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录接口，只做单层登录、登出与状态查询，口令不落库不记日志
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthProperties authProperties;

    private final CredentialResolver credentialResolver;

    private final SessionRegistry sessionRegistry;

    private final LoginAttemptGuard loginAttemptGuard;

    /**
     * 注入登录所需的四个组件
     *
     * @param authProperties   登录配置
     * @param credentialResolver 凭据校验
     * @param sessionRegistry  会话管理
     * @param loginAttemptGuard 失败计数
     */
    public AuthController(AuthProperties authProperties, CredentialResolver credentialResolver,
                          SessionRegistry sessionRegistry, LoginAttemptGuard loginAttemptGuard) {
        this.authProperties = authProperties;
        this.credentialResolver = credentialResolver;
        this.sessionRegistry = sessionRegistry;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    /**
     * 登录，连续失败达到阈值后临时锁定
     *
     * @param request  登录请求
     * @param response 响应，用于写 Cookie
     * @return 登录后的状态
     */
    @PostMapping("/login")
    public Result<AuthStatusVO> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        if (!authProperties.isEnabled()) {
            return Result.success(statusOf(true, 0L));
        }
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw new BusinessException(HttpsCodeEnum.PARAMS_ERROR, "用户名与口令不能为空");
        }
        String username = request.getUsername().trim();
        long locked = loginAttemptGuard.lockedSeconds(username);
        if (locked > 0) {
            // 锁定时不校验口令，直接拒绝，避免继续试错
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR,
                    "登录失败次数过多，已临时锁定，请 " + locked + " 秒后再试");
        }
        if (!credentialResolver.configured()) {
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR,
                    "尚未在密钥文件里配置 AUTH_USERNAME 与 AUTH_PASSWORD，无法登录");
        }
        if (!credentialResolver.matches(username, request.getPassword())) {
            int failures = loginAttemptGuard.recordFailure(username);
            log.warn("登录失败，已连续失败 {} 次", failures);
            throw new BusinessException(HttpsCodeEnum.USER_OR_PASSWORD_ERROR, "用户名或口令不正确");
        }
        loginAttemptGuard.recordSuccess(username);
        String token = sessionRegistry.create(username);
        response.addCookie(sessionCookie(token));
        log.info("登录成功");
        return Result.success(statusOf(true, (long) authProperties.getSessionHours() * 3600));
    }

    /**
     * 登出，作废会话并清掉 Cookie
     *
     * @param request  请求，用于取当前令牌
     * @param response 响应，用于清 Cookie
     * @return 空结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionRegistry.invalidate(tokenOf(request));
        Cookie cookie = sessionCookie("");
        // 过期时间设为 0 才会让浏览器真的删掉这条 Cookie
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return Result.success();
    }

    /**
     * 查询登录状态，未登录时也返回 200，由前端据 authenticated 决定是否跳转
     *
     * @param request 请求
     * @return 登录状态
     */
    @GetMapping("/status")
    public Result<AuthStatusVO> status(HttpServletRequest request) {
        boolean authenticated = !authProperties.isEnabled() || sessionRegistry.valid(tokenOf(request));
        return Result.success(statusOf(authenticated, 0L));
    }

    /**
     * 组装登录状态
     *
     * @param authenticated 是否已登录
     * @param remaining     剩余秒数
     * @return 状态
     */
    private AuthStatusVO statusOf(boolean authenticated, Long remaining) {
        AuthStatusVO vo = new AuthStatusVO();
        vo.setAuthenticated(authenticated);
        vo.setRequired(authProperties.isEnabled());
        vo.setConfigured(credentialResolver.configured());
        vo.setRemainingSeconds(remaining);
        return vo;
    }

    /**
     * 构造会话 Cookie，HttpOnly 防止脚本读取，SameSite 防跨站携带
     *
     * @param token 会话令牌
     * @return Cookie
     */
    private Cookie sessionCookie(String token) {
        Cookie cookie = new Cookie(authProperties.getCookieName(), token);
        cookie.setHttpOnly(true);
        cookie.setSecure(authProperties.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(authProperties.getSessionHours() * 3600);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    /**
     * 从请求里取会话令牌
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
}
