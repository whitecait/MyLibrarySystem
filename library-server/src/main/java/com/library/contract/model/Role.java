package com.library.contract.model;

/**
 * OpenAPI Role 枚举的服务端受控值。SYSTEM 角色不落 account 表
 * （V1 CHECK 仅 READER/LIBRARIAN/ADMIN），仅供闭馆等系统主体使用。
 */
public enum Role {
    READER, LIBRARIAN, ADMIN, SYSTEM
}
