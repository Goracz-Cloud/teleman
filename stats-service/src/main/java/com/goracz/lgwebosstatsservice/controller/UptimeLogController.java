package com.goracz.lgwebosstatsservice.controller;

import com.goracz.lgwebosstatsservice.entity.UptimeLog;
import com.goracz.lgwebosstatsservice.service.UptimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/uptime")
@RequiredArgsConstructor
public class UptimeLogController {
    private final UptimeService uptimeService;

    @GetMapping
    public ResponseEntity<Mono<UptimeLog>> getLatestUptimeLog() {
        return ResponseEntity.ok(this.uptimeService.getLatestUptimeLog());
    }
}