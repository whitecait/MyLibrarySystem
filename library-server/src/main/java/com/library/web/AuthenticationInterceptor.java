package com.library.web;

import com.library.contract.ModuleResult;
import com.library.contract.audit.AuditAppender;
import com.library.identity.contract.ActorResolver;
import com.library.identity.contract.SessionToken;
import com.library.web.support.Actors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Bearer 会话解析（opaque token → ActorContext）。令牌存在但无效 → 401 信封
 * （MLS-ID-001）；未携带令牌放行为匿名（PUBLIC 操作可用，受保护操作由用例拒绝）。
 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private final ActorResolver actorResolver;
    private final AuditAppender audit;

    public AuthenticationInterceptor(ActorResolver actorResolver, AuditAppender audit) {
        this.actorResolver = actorResolver;
        this.audit = audit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        var authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return true;
        }
        var token = authorization.substring("Bearer ".length()).trim();
        var resolved = actorResolver.requireActor(new SessionToken(token));
        if (resolved instanceof ModuleResult.Accepted<com.library.contract.security.ActorContext> accepted) {
            request.setAttribute(Actors.ACTOR_ATTRIBUTE, accepted.value());
            request.setAttribute(Actors.TOKEN_ATTRIBUTE, new SessionToken(token));
            return true;
        }
        if (resolved instanceof ModuleResult.Rejected<com.library.contract.security.ActorContext> rejected) {
            audit.append("ACCESS_DENIED", null,
                    java.util.Map.of("path", request.getRequestURI(), "code", rejected.code(), "status", 401));
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("""
                    {"code":"%s","message":"%s","data":{"reasons":["%s"]}}
                    """.formatted(rejected.code(),
                    rejected.reasons().isEmpty() ? rejected.code() : rejected.reasons().get(0),
                    rejected.reasons().isEmpty() ? "未认证" : rejected.reasons().get(0)).trim());
            return false;
        }
        return true;
    }
}
