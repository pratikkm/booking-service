package com.ticket.booking.controller;


import com.ticket.booking.config.AuthContext;
import com.ticket.booking.dto.ErrorResponse;
import com.ticket.booking.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ReservationService.ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ReservationService.ResourceNotFoundException e, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), request);
    }

    @ExceptionHandler(ReservationService.ValidationException.class)
    public ResponseEntity<ErrorResponse> badRequest(ReservationService.ValidationException e, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), request);
    }

    @ExceptionHandler(ReservationService.ForbiddenException.class)
    public ResponseEntity<ErrorResponse> forbidden(ReservationService.ForbiddenException e, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage(), request);
    }

    @ExceptionHandler(ReservationService.ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ReservationService.ConflictException e, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONFLICT", e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("request validation failed");
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformedJson(HttpMessageNotReadableException e, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_JSON", "request body is not valid JSON", request);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message, HttpServletRequest request) {
        Object requestId = request.getAttribute(AuthContext.REQUEST_ID_ATTRIBUTE);
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, requestId == null ? null : requestId.toString()));
    }
}
