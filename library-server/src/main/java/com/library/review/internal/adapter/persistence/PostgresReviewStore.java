package com.library.review.internal.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.contract.InfrastructureFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
public class PostgresReviewStore implements ReviewStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private static final RowMapper<StoredReview> ROW = (rs, i) -> new StoredReview(
            rs.getLong("id"), rs.getLong("applicant_id"), rs.getString("target_type"),
            rs.getLong("target_id"), rs.getString("action_type"), parsePayload(rs.getString("action_payload")),
            rs.getString("reason"), rs.getString("decision"),
            rs.getObject("target_version") == null ? 0 : rs.getLong("target_version"),
            rs.getString("target_outcome"), rs.getLong("version"));

    public PostgresReviewStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    private static Map<String, Object> parsePayload(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return new ObjectMapper().readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new InfrastructureFailure("复核负载解析失败", e);
        }
    }

    @Override
    public long insert(NewReview candidate) {
        try {
            var payloadJson = mapper.writeValueAsString(candidate.payload() == null ? Map.of() : candidate.payload());
            return jdbc.queryForObject("""
                    INSERT INTO review_request (applicant_id, target_type, target_id, reason,
                                                action_type, action_payload, target_version)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                    RETURNING id
                    """, Long.class, candidate.applicantId(), candidate.targetType(), candidate.targetId(),
                    candidate.reason(), candidate.action(), payloadJson, candidate.targetVersion());
        } catch (JsonProcessingException e) {
            throw new InfrastructureFailure("复核负载序列化失败", e);
        }
    }

    @Override
    public Optional<StoredReview> byId(long reviewId) {
        return jdbc.query("SELECT * FROM review_request WHERE id = ?", ROW, reviewId).stream().findFirst();
    }
}
