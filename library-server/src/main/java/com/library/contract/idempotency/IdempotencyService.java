package com.library.contract.idempotency;

import com.library.contract.ModuleResult;

import java.util.function.Supplier;

/**
 * 幂等处理（G0 共同机制；BR-0042/X2）：写请求在首次效果与重放之间保证
 * 「同键同摘要返回原结果」。键行插入、业务执行与结果快照在同一事务内提交，
 * 并发同键请求依靠 request_key 唯一索引串行化：后到者重放首次结果。
 */
public interface IdempotencyService {

    /**
     * @param scope       作用域（主体+方法+规范路径）
     * @param key         客户端 Idempotency-Key 头（1~128 字符）
     * @param requestHash 请求规范摘要（SHA-256 hex）
     * @param successCode 业务成功时的模块稳定码（如 MLS-ID-S001）
     * @param dataType    成功结果反序列化类型（重放需要）
     * @param work        业务执行，返回 Accepted 或业务 Rejected
     */
    <T> IdempotentOutcome<T> execute(IdempotencyScope scope,
                                     String key,
                                     String requestHash,
                                     String successCode,
                                     Class<T> dataType,
                                     Supplier<ModuleResult<T>> work);
}
