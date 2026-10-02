package com.library.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.application.registration.RegistrationAttemptStore;
import com.library.contract.error.StableErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Counts every registration HTTP request before body parsing and idempotency replay. */
@Component
public class RegistrationRateLimitFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "mls_registration_session";
    private static final int REQUEST_LIMIT = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegistrationAttemptStore attempts;
    private final ObjectMapper json;

    public RegistrationRateLimitFilter(RegistrationAttemptStore attempts, ObjectMapper json) {
        this.attempts = attempts;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/v1/auth/registrations".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var sessionId = sessionCookie(request);
        int requestCount;
        if (sessionId == null) {
            sessionId = newSessionId();
            attempts.issueSessionAndRecord(sessionId);
            issueCookie(request, response, sessionId);
            requestCount = 1;
        } else {
            var existingCount = attempts.recordRequest(sessionId);
            if (existingCount.isEmpty()) {
                // 未由服务端签发的伪造/过期标识不会作为限流身份接受。
                sessionId = newSessionId();
                attempts.issueSessionAndRecord(sessionId);
                issueCookie(request, response, sessionId);
                requestCount = 1;
            } else {
                requestCount = existingCount.getAsInt();
            }
        }
        if (requestCount > REQUEST_LIMIT) {
            writeError(response, 429, StableErrorCode.MLS_ID_008.code(),
                    "此浏览器会话的注册请求已达到每小时五次上限", "registration request quota exhausted");
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Scheduled(cron = "0 23 * * * *", zone = "Asia/Shanghai")
    public void purgeExpiredAttempts() {
        attempts.purgeExpired();
    }

    private void writeError(HttpServletResponse response, int status, String code,
                            String message, String reason) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), Envelope.of(code, message, Map.of("reasons", List.of(reason))));
    }

    private static String sessionCookie(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) return null;
        for (var cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null
                    && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) return cookie.getValue();
        }
        return null;
    }

    private static String newSessionId() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void issueCookie(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        response.addHeader("Set-Cookie", COOKIE_NAME + "=" + sessionId + "; Path=/"
                + (request.isSecure() ? "; Secure" : "") + "; HttpOnly; SameSite=Strict");
    }
}
