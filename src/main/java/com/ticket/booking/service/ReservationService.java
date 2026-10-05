package com.ticket.booking.service;

import com.ticket.booking.metrics.MetricsService;
import tools.jackson.databind.ObjectMapper;
import com.ticket.booking.dto.ReservationResponse;
import com.ticket.booking.repository.IdempotencyRepository;
import com.ticket.booking.repository.ReservationRepository;
import com.ticket.booking.repository.SeatRepository;
import com.ticket.booking.repository.ShowRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {
    private static final int CONFLICT = 409;
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    public ReservationService(ShowRepository showRepository,
                              SeatRepository seatRepository,
                              ReservationRepository reservationRepository,
                              IdempotencyRepository idempotencyRepository,
                              JdbcTemplate jdbc,
                              ObjectMapper objectMapper, MetricsService metricsService) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    @Transactional
    public ReservationResult reserve(UUID showId, String userId, List<String> requestedSeats, String idempotencyKey) {
        if (!showRepository.exists(showId)) {
            throw new ResourceNotFoundException("show not found");
        }
        List<String> seats = canonicalSeats(requestedSeats);
        if (seats.isEmpty()) {
            throw new ValidationException("at least one seat is required");
        }
        if (seats.size() != requestedSeats.size()) {
            throw new ValidationException("duplicate seat in request");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw new ValidationException("idempotency_key must be non-empty and at most 200 characters");
        }

        String requestHash = requestHash(seats);
        idempotencyRepository.insertIfAbsent(showId, userId, idempotencyKey, requestHash);
        IdempotencyRepository.IdempotencyRecord key = idempotencyRepository.lockByKey(showId, userId, idempotencyKey);
        if (key == null) {
            throw new IllegalStateException("idempotency row disappeared");
        }
        if (!key.requestHash().equals(requestHash)) {
            ReservationResult mismatch = decline(CONFLICT, "idempotent-key-mismatch");
            return mismatch;
        }
        if (key.responseStatus() != null) {
            Object replay = readStoredResponse(key.responseBody());
            afterCommit(metricsService::incrementIdempotentReplay);
            return new ReservationResult(key.responseStatus(), replay, true, "idempotent-replay");
        }

        // Serialize only reservations/cancellations for the same user and show.
        // Different users can still proceed in parallel.
        lockUserAndShow(showId, userId);

        int currentCount = reservationRepository.countConfirmedSeatsForUser(showId, userId);
        int perUserLimit = showRepository.getPerUserLimit(showId);
        if (currentCount + seats.size() > perUserLimit) {
            ReservationResult declined = decline(CONFLICT, "per-user-limit");
            completeIdempotency(key.id(), declined);
            return declined;
        }

        List<SeatRepository.LockedSeat> lockedSeats = seatRepository.lockRequestedSeats(showId, seats);
        if (lockedSeats.size() != seats.size()) {
            ReservationResult declined = decline(CONFLICT, "seat-taken");
            completeIdempotency(key.id(), declined);
            return declined;
        }

        boolean allAvailable = lockedSeats.stream().allMatch(s -> "AVAILABLE".equals(s.status()));
        if (!allAvailable) {
            ReservationResult declined = decline(CONFLICT, "seat-taken");
            completeIdempotency(key.id(), declined);
            return declined;
        }

        UUID reservationId = UUID.randomUUID();
        long unitPricePaise = showRepository.getPricePaise(showId);
        if (seats.size() > Long.MAX_VALUE / Math.max(1L, unitPricePaise)) {
            throw new ValidationException("reservation amount exceeds supported integer range");
        }
        long amountPaise = Math.multiplyExact(unitPricePaise, seats.size());
        List<Long> seatIds = lockedSeats.stream().map(SeatRepository.LockedSeat::id).toList();

        seatRepository.markConfirmed(seatIds);
        reservationRepository.insertReservation(reservationId, showId, userId, amountPaise, seatIds);
        ReservationResponse response = new ReservationResponse(
                reservationId.toString(), showId.toString(), userId, seats, amountPaise, "confirmed");
        ReservationResult result = new ReservationResult(201, response, false, null);
        completeIdempotency(key.id(), result);
        afterCommit(() -> metricsService.incrementConfirmed());
        return result;
    }

    @Transactional
    public ReservationResponse cancel(UUID reservationId, String userId) {
        ReservationRepository.ReservationRecord reservation = reservationRepository.lockReservation(reservationId);
        if (reservation == null) {
            throw new ResourceNotFoundException("reservation not found");
        }
        if (!reservation.userId().equals(userId)) {
            throw new ForbiddenException("only the reservation owner can cancel it");
        }
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new ConflictException("reservation is already cancelled");
        }

        lockUserAndShow(reservation.showId(), userId);
        List<String> originalSeats = reservationRepository.lockSeatsByReservation(reservationId).stream()
                .map(ReservationRepository.SeatRecord::seatNumber)
                .sorted()
                .toList();
        reservationRepository.releaseSeats(reservationId);
        reservationRepository.cancel(reservationId);
        afterCommit(() -> metricsService.refreshShowGauge(reservation.showId()));

        return new ReservationResponse(
                reservation.id().toString(),
                reservation.showId().toString(),
                reservation.userId(),
                originalSeats,
                reservation.amountPaise(),
                "cancelled");
    }

    private ReservationResult decline(int httpStatus, String reason) {
        afterCommit(() -> metricsService.incrementDeclined(reason));
        DeclineBody body = new DeclineBody("RESERVATION_DECLINED", reason, humanReason(reason));
        return new ReservationResult(httpStatus, body, false, reason);
    }

    private static String humanReason(String reason) {
        return switch (reason) {
            case "seat-taken" -> "one or more requested seats are already taken";
            case "per-user-limit" -> "reservation would exceed the per-user seat limit";
            case "idempotent-key-mismatch" -> "idempotency key was already used with a different request";
            default -> "reservation declined";
        };
    }

    public record DeclineBody(String code, String reason, String message) {}

    private void completeIdempotency(long id, ReservationResult result) {
        String body;
        body = objectMapper.writeValueAsString(result.body());
        UUID reservationId = result.body() instanceof ReservationResponse response
                ? UUID.fromString(response.reservation_id())
                : null;
        idempotencyRepository.complete(id, result.httpStatus(), reservationId, body);
    }

    private Object readStoredResponse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("corrupt idempotency response", e);
        }
    }

    private void lockUserAndShow(UUID showId, String userId) {
        String lockKey = showId + ":" + userId;

        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> null,
                lockKey
        );
    }

    private static List<String> canonicalSeats(List<String> seats) {
        if (seats == null) return List.of();
        return seats.stream().map(String::trim).sorted().toList();
    }

    private static String requestHash(List<String> seats) {
        String canonical = String.join("\u001f", seats);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void afterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) { super(message); }
    }
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) { super(message); }
    }
}
