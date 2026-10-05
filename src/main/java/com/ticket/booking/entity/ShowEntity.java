package com.ticket.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shows")
public class ShowEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Column(name = "per_user_limit", nullable = false)
    private int perUserLimit;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ShowEntity() {
    }

    public ShowEntity(UUID id, String name, long pricePaise, int perUserLimit) {
        this.id = id;
        this.name = name;
        this.pricePaise = pricePaise;
        this.perUserLimit = perUserLimit;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public long getPricePaise() { return pricePaise; }
    public int getPerUserLimit() { return perUserLimit; }
    public Instant getCreatedAt() { return createdAt; }
}
