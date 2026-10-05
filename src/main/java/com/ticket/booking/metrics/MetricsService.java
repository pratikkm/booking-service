package com.ticket.booking.metrics;

import com.ticket.booking.repository.ShowRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MetricsService {
    private final MeterRegistry registry;
    private final ShowRepository showRepository;
    private final Counter confirmed;
    private final ConcurrentHashMap<String, Counter> declined = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> registeredShows = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry registry, ShowRepository showRepository) {
        this.registry = registry;
        this.showRepository = showRepository;
        this.confirmed = Counter.builder("reservations_confirmed_total")
                .description("Confirmed reservations, counting each reservation once")
                .register(registry);
        counterForReason("seat-taken");
        counterForReason("per-user-limit");
        counterForReason("idempotent-replay");
        counterForReason("idempotent-key-mismatch");
    }

    public void incrementConfirmed() {
        confirmed.increment();
    }

    public void incrementIdempotentReplay() {
        declined.get("idempotent-replay").increment();
    }

    public void incrementDeclined(String reason) {
        counterForReason(reason).increment();
    }

    private Counter counterForReason(String reason) {
        return declined.computeIfAbsent(reason, r -> Counter.builder("reservations_declined_total")
                .description("Reservation responses by domain reason; idempotent-replay is a replay, not a new decline")
                .tag("reason", r)
                .register(registry));
    }

    public void registerShowGauge(UUID showId) {
        if (registeredShows.putIfAbsent(showId, Boolean.TRUE) == null) {
            Gauge.builder("seats_available", showRepository, repo -> repo.countAvailable(showId))
                    .description("Current available seats for a show")
                    .tag("show_id", showId.toString())
                    .register(registry);
        }
    }

    public void refreshShowGauge(UUID showId) {
        registerShowGauge(showId);
    }
}
