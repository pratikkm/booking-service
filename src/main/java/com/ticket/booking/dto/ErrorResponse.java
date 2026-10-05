package com.ticket.booking.dto;

public record ErrorResponse(String code, String message, String request_id) {}
