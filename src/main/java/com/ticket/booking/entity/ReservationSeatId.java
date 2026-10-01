package com.ticket.booking.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ReservationSeatId implements Serializable {
    private UUID reservationId;
    private Long showSeatId;

    protected ReservationSeatId() {
    }

    public ReservationSeatId(UUID reservationId, Long showSeatId) {
        this.reservationId = reservationId;
        this.showSeatId = showSeatId;
    }

    public UUID getReservationId() { return reservationId; }
    public Long getShowSeatId() { return showSeatId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationSeatId that)) return false;
        return Objects.equals(reservationId, that.reservationId) && Objects.equals(showSeatId, that.showSeatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId, showSeatId);
    }
}
