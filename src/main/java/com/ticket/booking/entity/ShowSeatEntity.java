package com.ticket.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "show_seats", uniqueConstraints = @UniqueConstraint(name = "uk_show_seats_show_seat", columnNames = {"show_id", "seat_number"}))
public class ShowSeatEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private ShowEntity show;

    @Column(name = "seat_number", nullable = false, length = 100)
    private String seatNumber;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ShowSeatEntity() {
    }

    public ShowSeatEntity(ShowEntity show, String seatNumber, String status) {
        this.show = show;
        this.seatNumber = seatNumber;
        this.status = status;
    }

    public Long getId() { return id; }
    public UUID getShowId() { return show.getId(); }
    public ShowEntity getShow() { return show; }
    public String getSeatNumber() { return seatNumber; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(String status) { this.status = status; }
}
