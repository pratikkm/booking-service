package com.ticket.booking.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class IdempotencyRepository {
    private final JdbcTemplate jdbc;

    public IdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertIfAbsent(UUID showId, String userId, String key, String requestHash) {
        jdbc.update("""
                INSERT INTO idempotency_keys(show_id, user_id, idempotency_key, request_hash)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (show_id, user_id, idempotency_key) DO NOTHING
                """, showId, userId, key, requestHash);
    }

    public IdempotencyRecord lockByKey(UUID showId, String userId, String key) {
        return jdbc.query("""
                SELECT id, request_hash, response_status, reservation_id, response_body
                  FROM idempotency_keys
                 WHERE show_id = ? AND user_id = ? AND idempotency_key = ?
                 FOR UPDATE
                """, rs -> {
            if (!rs.next()) return null;
            return new IdempotencyRecord(
                    rs.getLong("id"),
                    rs.getString("request_hash"),
                    (Integer) rs.getObject("response_status"),
                    rs.getObject("reservation_id", UUID.class),
                    rs.getString("response_body")
            );
        }, showId, userId, key);
    }

    public void complete(long id, int responseStatus, UUID reservationId, String responseBody) {
        jdbc.update("""
                UPDATE idempotency_keys
                   SET response_status = ?, reservation_id = ?, response_body = ?
                 WHERE id = ?
                """, responseStatus, reservationId, responseBody, id);
    }

    public record IdempotencyRecord(long id, String requestHash, Integer responseStatus, UUID reservationId, String responseBody) {}
}
