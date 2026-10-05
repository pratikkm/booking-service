package com.ticket.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_seats")
public class ReservationSeatEntity {
    @EmbeddedId
    private ReservationSeatId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("reservationId")
    @JoinColumn(name = "reservation_id", nullable = false)
    private ReservationEntity reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("showSeatId")
    @JoinColumn(name = "show_seat_id", nullable = false)
    private ShowSeatEntity showSeat;

    @Column(name = "seat_number", nullable = false, length = 100)
    private String seatNumber;

    protected ReservationSeatEntity() {
    }

    public ReservationSeatEntity(ReservationEntity reservation, ShowSeatEntity showSeat, String seatNumber) {
        this.reservation = reservation;
        this.showSeat = showSeat;
        this.seatNumber = seatNumber;
        this.id = new ReservationSeatId(reservation.getId(), showSeat.getId());
    }

    public ReservationSeatId getId() { return id; }
    public ReservationEntity getReservation() { return reservation; }
    public ShowSeatEntity getShowSeat() { return showSeat; }
    public String getSeatNumber() { return seatNumber; }
}
