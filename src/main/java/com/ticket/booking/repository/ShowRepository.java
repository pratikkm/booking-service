package com.ticket.booking.repository;

import com.ticket.booking.dto.SeatView;
import com.ticket.booking.dto.ShowResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class ShowRepository {
    private final JdbcTemplate jdbc;

    public ShowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(UUID showId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM shows WHERE id = ?", Integer.class, showId);
        return count != null && count > 0;
    }

    @Transactional
    public ShowResponse create(UUID id, String name, List<String> seats, long pricePaise, int perUserLimit) {
        jdbc.update("INSERT INTO shows(id, name, price_paise, per_user_limit) VALUES (?, ?, ?, ?)",
                id, name, pricePaise, perUserLimit);
        jdbc.batchUpdate(
                "INSERT INTO show_seats(show_id, seat_number, status) VALUES (?, ?, 'AVAILABLE')",
                seats,
                seats.size(),
                (ps, seat) -> {
                    ps.setObject(1, id);
                    ps.setString(2, seat);
                });
        return get(id);
    }

    @Transactional(readOnly = true)
    public ShowResponse get(UUID id) {
        ShowSummary summary = jdbc.query("""
                SELECT s.id, s.name, s.price_paise, s.per_user_limit,
                       COUNT(ss.id) AS total_seats,
                       COUNT(*) FILTER (WHERE ss.status = 'AVAILABLE') AS available_seats,
                       0 AS held_seats,
                       COUNT(*) FILTER (WHERE ss.status = 'CONFIRMED') AS confirmed_seats
                  FROM shows s
                  JOIN show_seats ss ON ss.show_id = s.id
                 WHERE s.id = ?
                 GROUP BY s.id, s.name, s.price_paise, s.per_user_limit
                """, rs -> {
            if (!rs.next()) return null;
            return new ShowSummary(
                    rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("price_paise"),
                    rs.getInt("per_user_limit"), rs.getInt("total_seats"), rs.getInt("available_seats"),
                    rs.getInt("held_seats"), rs.getInt("confirmed_seats"));
        }, id);
        if (summary == null) return null;
        return new ShowResponse(
                summary.id().toString(), summary.name(), summary.pricePaise(), summary.perUserLimit(),
                summary.totalSeats(), summary.availableSeats(), summary.heldSeats(), summary.confirmedSeats(),
                listSeats(id));
    }

    public int countAvailable(UUID showId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM show_seats WHERE show_id = ? AND status = 'AVAILABLE'",
                Integer.class, showId);
        return count == null ? 0 : count;
    }

    public int getPerUserLimit(UUID showId) {
        Integer limit = jdbc.queryForObject("SELECT per_user_limit FROM shows WHERE id = ?", Integer.class, showId);
        return limit == null ? 0 : limit;
    }

    public long getPricePaise(UUID showId) {
        Long price = jdbc.queryForObject("SELECT price_paise FROM shows WHERE id = ?", Long.class, showId);
        return price == null ? 0L : price;
    }

    private List<SeatView> listSeats(UUID showId) {
        return jdbc.query("SELECT seat_number, LOWER(status) AS status FROM show_seats WHERE show_id = ? ORDER BY seat_number",
                (rs, rowNum) -> new SeatView(rs.getString("seat_number"), rs.getString("status")), showId);
    }

    private record ShowSummary(UUID id, String name, long pricePaise, int perUserLimit,
                               int totalSeats, int availableSeats, int heldSeats, int confirmedSeats) {}
}
