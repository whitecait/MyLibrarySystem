package com.library.web;

import com.library.ai.contract.VisitorOwnerDigest;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 原始访客凭据只停留在 HTTP Cookie 边界。 */
@Component
public class AiVisitorCookieAdapter {
    public static final String NAME="__Host-mls-ai-visitor";
    private final SecureRandom random=new SecureRandom();
    public Issued issue(){ var bytes=new byte[32];random.nextBytes(bytes);var raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);return new Issued(raw,digest(raw)); }
    public VisitorOwnerDigest from(Cookie[] cookies){
        if(cookies==null)return null;
        for(var cookie:cookies) if(NAME.equals(cookie.getName())&&!cookie.getValue().isBlank()) return digest(cookie.getValue());
        return null;
    }
    public VisitorOwnerDigest digest(String raw){
        try{return new VisitorOwnerDigest(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public record Issued(String raw,VisitorOwnerDigest digest){}
}
