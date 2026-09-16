package com.library.web.support;

import com.library.contract.security.ActorContext;
import com.library.identity.contract.SessionToken;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求内认证与字段错误码属性访问。
 */
public final class Actors {

    public static final String ACTOR_ATTRIBUTE = Actors.class.getName() + ".actor";
    public static final String TOKEN_ATTRIBUTE = Actors.class.getName() + ".token";

    private Actors() {
    }

    public static ActorContext actor(HttpServletRequest request) {
        return (ActorContext) request.getAttribute(ACTOR_ATTRIBUTE);
    }

    public static SessionToken token(HttpServletRequest request) {
        return (SessionToken) request.getAttribute(TOKEN_ATTRIBUTE);
    }

    public static Long actorId(HttpServletRequest request) {
        var actor = actor(request);
        return actor == null ? null : actor.accountId().value();
    }

    public static void declareFieldErrorCode(HttpServletRequest request, String code) {
        request.setAttribute(
                com.library.web.ApiExceptionHandler.FIELD_ERROR_CODE_ATTRIBUTE, code);
    }
}
