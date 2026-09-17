package com.frauddetection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.dto.AlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlertBroadcastService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;
    private final Neo4jClient neo4jClient;

    // Registers a new SSE connection — one per open dashboard tab — and wires up cleanup so a
    // closed or dropped connection removes itself from the broadcast list instead of leaking.
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        log.debug("New SSE subscriber. Total: {}", emitters.size());
        return emitter;
    }

    // Single entry point for raising an alert: writes it durably first, then pushes it live to
    // every connected dashboard. Called once per transaction that crosses the risk threshold.
    public void persistAndBroadcast(AlertEvent event) {
        persist(event);
        broadcast(event);
    }

    // Writes a brand-new Alert node — CREATE, not MERGE, since every alert is a distinct event.
    // The same account can be flagged more than once and must keep a separate entry each time,
    // which is also why Alert has no uniqueness constraint in Neo4jSchemaConfig.
    private void persist(AlertEvent event) {
        neo4jClient.query("""
            CREATE (:Alert {
                accountId:     $accountId,
                transactionId: $transactionId,
                riskScore:     $riskScore,
                riskLevel:     $riskLevel,
                message:       $message,
                timestamp:     $timestamp
            })
            """)
            .bind(event.accountId()).to("accountId")
            .bind(event.transactionId()).to("transactionId")
            .bind(event.riskScore()).to("riskScore")
            .bind(event.riskLevel()).to("riskLevel")
            .bind(event.message()).to("message")
            .bind(event.timestamp()).to("timestamp")
            .run();
    }

    // Returns the most recent alerts, newest first, so the frontend can hydrate its alert feed
    // on page load — this is what lets alerts survive a refresh instead of only living in the
    // browser's in-memory state.
    public List<AlertEvent> history(int limit) {
        return neo4jClient.query("""
            MATCH (a:Alert)
            RETURN a.accountId AS accountId, a.transactionId AS transactionId,
                   a.riskScore AS riskScore, a.riskLevel AS riskLevel,
                   a.message AS message, a.timestamp AS timestamp
            ORDER BY a.timestamp DESC
            LIMIT $limit
            """)
            .bind(limit).to("limit")
            .fetch()
            .all()
            .stream()
            .map(this::toAlertEvent)
            .toList();
    }

    // Maps one raw Neo4j row back into an AlertEvent, so history() returns the exact same typed
    // shape that live SSE pushes use — callers never need to care whether an alert came from a
    // fresh broadcast or from this durable read.
    private AlertEvent toAlertEvent(Map<String, Object> row) {
        return AlertEvent.builder()
            .accountId((String) row.get("accountId"))
            .transactionId((String) row.get("transactionId"))
            .riskScore(((Number) row.get("riskScore")).doubleValue())
            .riskLevel((String) row.get("riskLevel"))
            .message((String) row.get("message"))
            .timestamp((java.time.LocalDateTime) row.get("timestamp"))
            .build();
    }

    // Pushes the event to every currently-connected dashboard over SSE. A failed send marks
    // that connection dead so one dropped tab doesn't stop delivery to everyone else.
    public void broadcast(AlertEvent event) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("alert")
                    .data(objectMapper.writeValueAsString(event)));
            } catch (IOException ex) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
        log.info("Alert broadcast to {} subscribers for account {}", emitters.size(), event.accountId());
    }
}
