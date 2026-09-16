package com.library.identity.contract;

/**
 * IDENTITY 聚合接口：仅供 com.library.bootstrap 装配与一致性检查
 * （MLS-DOC-MD-0002 §3），不得注入应用用例。能力子接口随批次实现加入。
 */
public interface IdentityModule extends IdentityRegistration {
}
