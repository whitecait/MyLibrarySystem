package com.library.application.registration;

import java.util.OptionalInt;

/** 原子登记匿名浏览器会话的注册请求次数；每个到达的请求都计数，包括业务拒绝。 */
public interface RegistrationAttemptStore {

    int issueSessionAndRecord(String sessionId);

    OptionalInt recordRequest(String sessionId);

    void purgeExpired();
}
