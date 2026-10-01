package com.ticket.booking.repository.jpa;

import com.ticket.booking.entity.ShowSeatEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowSeatJpaRepository extends JpaRepository<ShowSeatEntity, Long> {
}
