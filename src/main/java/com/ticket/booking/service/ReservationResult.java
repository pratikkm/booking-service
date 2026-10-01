package com.ticket.booking.service;

public record ReservationResult(int httpStatus, Object body, boolean replay, String declineReason) {}
