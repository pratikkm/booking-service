package com.ticket.booking.repository;

import com.ticket.booking.dto.SeatView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class SeatRepository {
    private final JdbcTemplate jdbc;

    public SeatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LockedSeat> lockRequestedSeats(UUID showId, List<String> seatNumbers) {
        String placeholders = String.join(",", java.util.Collections.nCopies(seatNumbers.size(), "?"));
        String sql = """
                SELECT id, seat_number, status
                  FROM show_seats
                 WHERE show_id = ? AND seat_number IN (""" + placeholders + ") ORDER BY seat_number FOR UPDATE";
        List<Object> args = new ArrayList<>();
        args.add(showId);
        args.addAll(seatNumbers);
        return jdbc.query(sql, (rs, rowNum) -> new LockedSeat(
                rs.getLong("id"), rs.getString("seat_number"), rs.getString("status")), args.toArray());
    }

    public void markConfirmed(List<Long> seatIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(seatIds.size(), "?"));
        List<Object> args = new ArrayList<>(seatIds);
        jdbc.update("UPDATE show_seats SET status = 'CONFIRMED' WHERE id IN (" + placeholders + ")", args.toArray());
    }

    public List<SeatView> seatsForShow(UUID showId) {
        return jdbc.query("SELECT seat_number, LOWER(status) AS status FROM show_seats WHERE show_id = ? ORDER BY seat_number",
                (rs, rowNum) -> new SeatView(rs.getString("seat_number"), rs.getString("status")), showId);
    }


    public record LockedSeat(long id, String seatNumber, String status) {}
}
