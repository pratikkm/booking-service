package com.ticket.booking.repository.jpa;

import com.ticket.booking.entity.ShowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShowJpaRepository extends JpaRepository<ShowEntity, UUID> {
}
