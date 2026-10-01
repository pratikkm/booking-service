package com.ticket.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class ReservationEntity {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private ShowEntity show;

    @Column(name = "user_id", nullable = false, length = 200)
    private String userId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected ReservationEntity() {
    }

    public ReservationEntity(UUID id, ShowEntity show, String userId, long amountPaise, String status) {
        this.id = id;
        this.show = show;
        this.userId = userId;
        this.amountPaise = amountPaise;
        this.status = status;
    }

    public UUID getId() { return id; }
    public ShowEntity getShow() { return show; }
    public UUID getShowId() { return show.getId(); }
    public String getUserId() { return userId; }
    public long getAmountPaise() { return amountPaise; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void cancelNow(Instant timestamp) { this.status = "CANCELLED"; this.cancelledAt = timestamp; }
}
