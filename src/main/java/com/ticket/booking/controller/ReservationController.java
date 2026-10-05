package com.ticket.booking.controller;

import com.ticket.booking.config.AuthContext;
import com.ticket.booking.dto.ReservationResponse;
import com.ticket.booking.dto.ReserveRequest;
import com.ticket.booking.service.ReservationResult;
import com.ticket.booking.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/shows/{id}/reserve")
    public ResponseEntity<?> reserve(@PathVariable UUID id,
                                     @Valid @RequestBody ReserveRequest request,
                                     HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute(AuthContext.USER_ID_ATTRIBUTE);
        MDC.put("show_id", id.toString());
        MDC.put("user_id", userId);
        try {
            ReservationResult result = reservationService.reserve(id, userId, request.seats(), request.idempotency_key());
            return ResponseEntity.status(result.httpStatus()).body(result.body());
        } finally {
            MDC.remove("show_id");
            MDC.remove("user_id");
        }
    }

    @PostMapping("/reservations/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable UUID id,
                                                      HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute(AuthContext.USER_ID_ATTRIBUTE);
        return ResponseEntity.ok(reservationService.cancel(id, userId));
    }
}
