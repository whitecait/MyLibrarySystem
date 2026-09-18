package com.library.web;

import com.library.ai.contract.VisitorOwnerDigest;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 原始访客凭据只停留在 HTTP Cookie 边界。 */
@Component
public class AiVisitorCookieAdapter {
    public static final String NAME="__Host-mls-ai-visitor";
    private final SecureRandom random=new SecureRandom();
    private final byte[] signingKey;
    public AiVisitorCookieAdapter(@Value("${library.ai.visitor-cookie-signing-key}") String signingKey){
        if(signingKey==null||signingKey.isBlank()||signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32)
            throw new IllegalStateException("AI visitor cookie signing key 至少 32 字节");
        this.signingKey=signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    public Issued issue(){ var bytes=new byte[32];random.nextBytes(bytes);var raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);return new Issued(raw,digest(raw)); }
    /** 无 Cookie 的启动按幂等键确定性恢复凭据；原值不持久化。 */
    public Issued recoverable(String idempotencyKey){
        try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(signingKey,"HmacSHA256"));var raw=Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("ai-start:"+idempotencyKey).getBytes(java.nio.charset.StandardCharsets.UTF_8)));return new Issued(raw,digest(raw));}
        catch(Exception e){throw new IllegalStateException("AI visitor cookie 签名失败",e);}
    }
    public String rawFrom(Cookie[] cookies){if(cookies==null)return null;for(var cookie:cookies)if(NAME.equals(cookie.getName())&&!cookie.getValue().isBlank())return cookie.getValue();return null;}
    public VisitorOwnerDigest from(Cookie[] cookies){
        var raw=rawFrom(cookies);return raw==null?null:digest(raw);
    }
    public VisitorOwnerDigest digest(String raw){
        try{return new VisitorOwnerDigest(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public record Issued(String raw,VisitorOwnerDigest digest){}
}
