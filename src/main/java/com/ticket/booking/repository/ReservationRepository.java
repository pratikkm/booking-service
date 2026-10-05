package com.ticket.booking.repository;

import com.ticket.booking.dto.ReservationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ReservationRepository {
    private final JdbcTemplate jdbc;

    public ReservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countConfirmedSeatsForUser(UUID showId, String userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM reservation_seats rs
                  JOIN reservations r ON r.id = rs.reservation_id
                 WHERE r.show_id = ?
                   AND r.user_id = ?
                   AND r.status = 'CONFIRMED'
                """, Integer.class, showId, userId);
        return count == null ? 0 : count;
    }

    public void insertReservation(UUID reservationId, UUID showId, String userId, long amountPaise, List<Long> seatIds) {
        jdbc.update("""
                INSERT INTO reservations(id, show_id, user_id, amount_paise, status)
                VALUES (?, ?, ?, ?, 'CONFIRMED')
                """, reservationId, showId, userId, amountPaise);

        jdbc.batchUpdate(
                "INSERT INTO reservation_seats(reservation_id, show_seat_id, seat_number) VALUES (?, ?, ?)",
                seatIds,
                seatIds.size(),
                (ps, seatId) -> {
                    ps.setObject(1, reservationId);
                    ps.setLong(2, seatId);
                    ps.setString(3, seatNumberById(seatId));
                });
    }

    private String seatNumberById(long seatId) {
        return jdbc.queryForObject("SELECT seat_number FROM show_seats WHERE id = ?", String.class, seatId);
    }

    public ReservationResponse find(UUID reservationId) {
        return jdbc.query("""
                SELECT r.id, r.show_id, r.user_id, r.amount_paise, r.status,
                       COALESCE((SELECT array_agg(rs.seat_number ORDER BY rs.seat_number)
                                   FROM reservation_seats rs WHERE rs.reservation_id = r.id), ARRAY[]::text[]) AS seats
                  FROM reservations r
                 WHERE r.id = ?
                """, rs -> {
            if (!rs.next()) return null;
            String[] seats = (String[]) rs.getArray("seats").getArray();
            return new ReservationResponse(
                    rs.getObject("id", UUID.class).toString(),
                    rs.getObject("show_id", UUID.class).toString(),
                    rs.getString("user_id"),
                    List.of(seats),
                    rs.getLong("amount_paise"),
                    rs.getString("status").toLowerCase()
            );
        }, reservationId);
    }

    public ReservationRecord lockReservation(UUID reservationId) {
        return jdbc.query("""
                SELECT id, show_id, user_id, amount_paise, status
                  FROM reservations
                 WHERE id = ?
                 FOR UPDATE
                """, rs -> {
            if (!rs.next()) return null;
            return new ReservationRecord(
                    rs.getObject("id", UUID.class),
                    rs.getObject("show_id", UUID.class),
                    rs.getString("user_id"),
                    rs.getLong("amount_paise"),
                    rs.getString("status")
            );
        }, reservationId);
    }

    public List<SeatRecord> lockSeatsByReservation(UUID reservationId) {
        return jdbc.query("""
                SELECT ss.id, ss.seat_number, ss.status
                  FROM reservation_seats rs
                  JOIN show_seats ss ON ss.id = rs.show_seat_id
                 WHERE rs.reservation_id = ?
                 ORDER BY ss.seat_number
                 FOR UPDATE OF ss
                """, (rs, rowNum) -> new SeatRecord(
                rs.getLong("id"), rs.getString("seat_number"), rs.getString("status")), reservationId);
    }

    public void cancel(UUID reservationId) {
        jdbc.update("UPDATE reservations SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE id = ?", reservationId);
    }

    public void releaseSeats(UUID reservationId) {
        jdbc.update("""
                UPDATE show_seats ss
                   SET status = 'AVAILABLE'
                  FROM reservation_seats rs
                 WHERE rs.reservation_id = ? AND ss.id = rs.show_seat_id
                """, reservationId);
        jdbc.update("DELETE FROM reservation_seats WHERE reservation_id = ?", reservationId);
    }

    public record ReservationRecord(UUID id, UUID showId, String userId, long amountPaise, String status) {}
    public record SeatRecord(long id, String seatNumber, String status) {}
}
