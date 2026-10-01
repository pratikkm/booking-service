package com.ticket.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ReserveRequest(
        @NotEmpty List<@NotBlank String> seats,
        @NotBlank String idempotency_key
) {}
