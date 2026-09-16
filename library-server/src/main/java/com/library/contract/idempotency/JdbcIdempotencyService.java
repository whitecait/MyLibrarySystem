package com.library.contract.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.library.contract.InfrastructureFailure;
import com.library.contract.ModuleResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

@Component
public class JdbcIdempotencyService implements IdempotencyService {

    /** 键保留期：重放窗口内的重复提交返回首次结果。 */
    static final Duration RETENTION = Duration.ofHours(24);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final com.library.contract.transaction.UnitOfWork unitOfWork;

    public JdbcIdempotencyService(JdbcTemplate jdbc,
                                  ObjectMapper mapper,
                                  com.library.contract.transaction.UnitOfWork unitOfWork) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.unitOfWork = unitOfWork;
    }

    /** 幂等键行主键：SHA-256(主体|方法|规范路径|键)，64 位 hex，满足 VARCHAR(128)。 */
    public static String scopeKey(IdempotencyScope scope, String key) {
        return sha256(scope.subjectId() + "|" + scope.method() + "|" + scope.canonicalPath() + "|" + key);
    }

    /** 请求规范摘要：对请求体的规范序列化取 SHA-256。 */
    public static String sha256(String canonical) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new InfrastructureFailure("SHA-256 不可用", e);
        }
    }

    @Override
    public <T> IdempotentOutcome<T> execute(IdempotencyScope scope,
                                            String key,
                                            String requestHash,
                                            String successCode,
                                            Class<T> dataType,
                                            Supplier<ModuleResult<T>> work) {
        return unitOfWork.inTransaction(() -> {
            var requestKey = scopeKey(scope, key);
            // ON CONFLICT DO NOTHING：并发同键时等待对方结束后按冲突处理，
            // 避免约束错误中止当前 PostgreSQL 事务（X2 幂等键并发）
            var inserted = jdbc.queryForList("""
                    INSERT INTO idempotency_key (request_key, request_hash, expires_at)
                    VALUES (?, ?, now() + make_interval(secs => ?))
                    ON CONFLICT (request_key) DO NOTHING
                    RETURNING id
                    """, Long.class, requestKey, requestHash, RETENTION.toSeconds());
            if (inserted.isEmpty()) {
                return replayOrConflict(requestKey, requestHash, dataType);
            }
            var result = work.get();
            var code = storeSnapshot(requestKey, successCode, result);
            return new IdempotentOutcome<>(IdempotentOutcome.Kind.FRESH, code, result);
        });
    }

    private <T> IdempotentOutcome<T> replayOrConflict(String requestKey, String requestHash, Class<T> dataType) {
        var row = jdbc.queryForList(
                "SELECT request_hash, response_snapshot FROM idempotency_key WHERE request_key = ?",
                requestKey);
        if (row.isEmpty()) {
            // 键行已被过期清理或尚未可见：视为可重试的确定性失败，等待下一请求重放。
            throw new InfrastructureFailure("幂等键行缺失");
        }
        var stored = row.get(0);
        if (!requestHash.equals(stored.get("request_hash"))) {
            return new IdempotentOutcome<>(IdempotentOutcome.Kind.CONFLICT,
                    com.library.contract.error.StableErrorCode.MLS_IDEMP_002.code(),
                    ModuleResult.Rejected.of(com.library.contract.error.StableErrorCode.MLS_IDEMP_002.code(),
                            "同一幂等键携带了不同的请求摘要"));
        }
        Object snapshotObj = stored.get("response_snapshot");
        if (snapshotObj == null) {
            throw new InfrastructureFailure("幂等键行缺少结果快照");
        }
        try {
            var snapshot = mapper.readTree(snapshotObj.toString());
            var kind = snapshot.get("kind").asText();
            var code = snapshot.get("code").asText();
            if ("ACCEPTED".equals(kind)) {
                T value = mapper.treeToValue(snapshot.get("value"), dataType);
                return new IdempotentOutcome<>(IdempotentOutcome.Kind.REPLAYED, code, ModuleResult.Accepted.of(value));
            }
            List<String> reasons = mapper.convertValue(snapshot.get("reasons"),
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            Long currentVersion = snapshot.get("currentVersion").isNull() ? null : snapshot.get("currentVersion").asLong();
            return new IdempotentOutcome<>(IdempotentOutcome.Kind.REPLAYED, code,
                    new ModuleResult.Rejected<>(code, reasons, currentVersion));
        } catch (InfrastructureFailure | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new InfrastructureFailure("幂等快照重放失败", e);
        }
    }

    private <T> String storeSnapshot(String requestKey, String successCode, ModuleResult<T> result) {
        ObjectNode snapshot = mapper.createObjectNode();
        String code;
        if (result instanceof ModuleResult.Accepted<T> accepted) {
            code = successCode;
            snapshot.put("kind", "ACCEPTED");
            snapshot.put("code", code);
            snapshot.set("value", mapper.valueToTree(accepted.value()));
        } else if (result instanceof ModuleResult.Rejected<T> rejected) {
            code = rejected.code();
            snapshot.put("kind", "REJECTED");
            snapshot.put("code", code);
            ArrayNode reasons = snapshot.putArray("reasons");
            rejected.reasons().forEach(reasons::add);
            snapshot.put("currentVersion", rejected.currentVersion());
        } else {
            throw new InfrastructureFailure("未知 ModuleResult 形态");
        }
        jdbc.update("UPDATE idempotency_key SET response_snapshot = ?::jsonb WHERE request_key = ?",
                snapshot.toString(), requestKey);
        return code;
    }
}
