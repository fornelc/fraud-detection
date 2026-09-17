package com.frauddetection.controller;

import com.frauddetection.dto.AlertEvent;
import com.frauddetection.service.AlertBroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * REST endpoints for the live fraud-alert feed: a Server-Sent Events stream for real-time
 * pushes, and a durable history lookup so the frontend can hydrate past alerts on page load.
 * Both endpoints are called from the same {@code useEffect} in the React app's {@code App.jsx}
 * on mount — see {@link AlertBroadcastService} for how each one is backed.
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertBroadcastService alertBroadcastService;

    /**
     * Opens a Server-Sent Events connection that stays open indefinitely, pushing a named
     * {@code "alert"} event to this specific client every time
     * {@link AlertBroadcastService#broadcast} fires.
     *
     * @return an emitter Spring keeps writing to until the client disconnects
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return alertBroadcastService.subscribe();
    }

    /**
     * Returns past alerts, most recent first, so the frontend can populate its alert feed on
     * page load without waiting for a new live event to arrive.
     *
     * @param limit maximum number of alerts to return; defaults to 50
     * @return alerts ordered by timestamp, newest first
     */
    @GetMapping("/history")
    public ResponseEntity<List<AlertEvent>> history(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(alertBroadcastService.history(limit));
    }
}
