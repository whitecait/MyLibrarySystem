package com.library.identity.contract;

/**
 * 读者状态快照（OpenAPI ReaderData：readerId/status/version）。
 */
public record ReaderSnapshot(com.library.identity.contract.model.ReaderId readerId,
                             String status,
                             long version) {
}
