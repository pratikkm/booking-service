package com.ticket.booking.controller;

import com.ticket.booking.dto.CreateShowRequest;
import com.ticket.booking.dto.ShowResponse;
import com.ticket.booking.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ShowController {
    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    public ResponseEntity<ShowResponse> create(@Valid @RequestBody CreateShowRequest request) {
        ShowResponse created = showService.create(request);
        return ResponseEntity.created(URI.create("/shows/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ShowResponse get(@PathVariable UUID id) {
        return showService.get(id);
    }
}
