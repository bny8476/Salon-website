package com.luxesuite.api.controller;

import com.luxesuite.api.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
// Security Note: @PreAuthorize is not required here because this endpoint generates a random UUID for anonymous stream subscriptions and does not expose sensitive data directly.
public class SseController {

    private final SseService sseService;

    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        String clientId = UUID.randomUUID().toString();
        return sseService.subscribe(clientId);
    }
}
