package com.ticket.booking.service;

import com.ticket.booking.dto.CreateShowRequest;
import com.ticket.booking.dto.ShowResponse;
import com.ticket.booking.repository.ShowRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ShowService {
    private final ShowRepository showRepository;
    private final int defaultPerUserLimit;

    public ShowService(ShowRepository showRepository,
                       @Value("${app.reservation.default-per-user-limit}") int defaultPerUserLimit) {
        this.showRepository = showRepository;
        this.defaultPerUserLimit = defaultPerUserLimit;
    }

    @Transactional
    public ShowResponse create(CreateShowRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ReservationService.ValidationException("name is required");
        }
        List<String> seats = request.seats().stream().map(String::trim).sorted().toList();
        if (seats.isEmpty()) throw new ReservationService.ValidationException("at least one seat is required");
        if (new HashSet<>(seats).size() != seats.size()) {
            throw new ReservationService.ValidationException("duplicate seat in show");
        }
        if (request.price_paise() < 0) {
            throw new ReservationService.ValidationException("price_paise must be non-negative");
        }
        int limit = request.per_user_limit() == null ? defaultPerUserLimit : request.per_user_limit();
        if (limit < 1) throw new ReservationService.ValidationException("per_user_limit must be >= 1");
        UUID id = UUID.randomUUID();
        ShowResponse response = showRepository.create(id, request.name().trim(), seats, request.price_paise(), limit);

        return response;
    }

    public ShowResponse get(UUID id) {
        ShowResponse response = showRepository.get(id);
        if (response == null) throw new ReservationService.ResourceNotFoundException("show not found");
        if (response.available_seats() + response.held_seats() + response.confirmed_seats() != response.total_seats()) {
            throw new IllegalStateException("show reconciliation invariant violated");
        }
        return response;
    }
}
