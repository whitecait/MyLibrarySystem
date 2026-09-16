package com.library.identity.contract;

/**
 * 注册命令（OpenAPI RegistrationRequest）。
 * 认证主体、服务端版本与业务时点由应用用例附加，不接受客户端覆盖。
 */
public record RegisterReader(String username, String password, String displayName) {
}
