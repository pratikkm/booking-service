package com.ticket.booking.dto;

import java.util.List;

public record ReservationResponse(
        String reservation_id,
        String show_id,
        String user_id,
        List<String> seats,
        long amount_paise,
        String status
) {}
