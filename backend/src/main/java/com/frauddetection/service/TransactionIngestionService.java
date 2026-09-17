package com.frauddetection.service;

import com.frauddetection.dto.AlertEvent;
import com.frauddetection.dto.RiskScoreResponse;
import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.dto.TransactionResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionIngestionService {

    private final Neo4jClient neo4jClient;
    private final RiskScoringService riskScoringService;
    private final AlertBroadcastService alertBroadcastService;

    private static final double HIGH_RISK_THRESHOLD = 0.7;

    // Orchestrates the full ingestion pipeline: write the subgraph, score the account against
    // known fraud, then flag + alert if the score crosses the threshold. @Transactional so a
    // failure partway through (e.g. Neo4j drops mid-write) rolls back rather than leaving a
    // transaction written with no risk score ever computed for it.
    @Transactional
    @CircuitBreaker(name = "neo4j")
    public TransactionResponse ingest(TransactionRequest req) {
        validateRequest(req);
        LocalDateTime now = LocalDateTime.now();

        writeSubgraph(req, now);

        double riskScore = scoreAndPersistRisk(req.accountId());
        boolean flagged = riskScore >= HIGH_RISK_THRESHOLD;
        if (flagged) {
            flagTransactionAndAlert(req, riskScore, now);
        }

        return new TransactionResponse(
                req.transactionId(),
                req.accountId(),
                req.amount(),
                riskScore,
                flagged,
                flagged ? "FLAGGED" : "COMPLETED",
                now
        );
    }

    // Builds the entire subgraph — account, device, IP, merchant, transaction, and all four
    // relationships — in one MERGE-based Cypher statement: a single round trip instead of nine
    // separate calls, and MERGE's create-if-absent semantics mean a device or account seen
    // before reuses the existing node instead of creating a duplicate.
    private void writeSubgraph(TransactionRequest req, LocalDateTime now) {
        neo4jClient.query("""
            MERGE (a:Account {accountId: $accountId})
            ON CREATE SET a.name        = $accountName,
                          a.email       = $accountEmail,
                          a.riskScore   = 0.0,
                          a.blacklisted = false

            MERGE (d:Device {deviceId: $deviceId})
            ON CREATE SET d.type        = $deviceType,
                          d.fingerprint = $deviceFingerprint

            MERGE (ip:IpAddress {address: $ipAddress})
            ON CREATE SET ip.country    = $ipCountry,
                          ip.suspicious = false

            MERGE (m:Merchant {merchantId: $merchantId})
            ON CREATE SET m.name        = $merchantName,
                          m.category    = $merchantCategory,
                          m.flagged     = false

            CREATE (t:Transaction {
                transactionId: $transactionId,
                amount:        $amount,
                timestamp:     $timestamp,
                flagged:       false,
                status:        'COMPLETED'
            })
            MERGE (a)-[:MADE]->(t)
            MERGE (t)-[:TO]->(m)
            MERGE (t)-[:USED]->(d)
            MERGE (t)-[:FROM]->(ip)
            """)
                .bindAll(params(req, now))
                .run();
    }

    // Delegates to RiskScoringService for the shortestPath-based decay score, then writes it
    // back onto the Account node so it's queryable later (e.g. the Risk Score Lookup card)
    // without recomputing it — the stored value stays accurate until the next transaction.
    private double scoreAndPersistRisk(String accountId) {
        RiskScoreResponse riskResult = riskScoringService.calculateRiskScore(accountId);
        double riskScore = riskResult.riskScore();

        neo4jClient.query("""
            MATCH (a:Account {accountId: $accountId})
            SET a.riskScore = $riskScore
            """)
                .bind(accountId).to("accountId")
                .bind(riskScore).to("riskScore")
                .run();

        return riskScore;
    }

    // Only called once riskScore crosses HIGH_RISK_THRESHOLD: marks the transaction flagged
    // in the graph, then persists + broadcasts the alert (Live Alerts feed and alert history)
    // so this event is visible immediately and still there after a page refresh.
    private void flagTransactionAndAlert(TransactionRequest req, double riskScore, LocalDateTime now) {
        neo4jClient.query("""
            MATCH (t:Transaction {transactionId: $transactionId})
            SET t.flagged = true, t.status = 'FLAGGED'
            """)
                .bind(req.transactionId()).to("transactionId")
                .run();

        alertBroadcastService.persistAndBroadcast(AlertEvent.builder()
                .accountId(req.accountId())
                .transactionId(req.transactionId())
                .riskScore(riskScore)
                .riskLevel(RiskScoreResponse.resolveLevel(riskScore))
                .message("High-risk transaction detected — possible fraud ring membership")
                .timestamp(now)
                .build());

        log.warn("HIGH-RISK transaction {} for account {} (score={})",
                req.transactionId(), req.accountId(), riskScore);
    }

    private Map<String, Object> params(TransactionRequest req, LocalDateTime now) {
        Map<String, Object> map = new HashMap<>();
        map.put("accountId",         req.accountId());
        map.put("accountName",       req.accountName());
        map.put("accountEmail",      req.accountEmail());
        map.put("deviceId",          req.deviceId());
        map.put("deviceType",        req.deviceType());
        map.put("deviceFingerprint", req.deviceFingerprint());
        map.put("ipAddress",         req.ipAddress());
        map.put("ipCountry",         req.ipCountry());
        map.put("merchantId",        req.merchantId());
        map.put("merchantName",      req.merchantName());
        map.put("merchantCategory",  req.merchantCategory());
        map.put("transactionId",     req.transactionId());
        map.put("amount",            req.amount());
        map.put("timestamp",         now);
        return map;
    }

    private void validateRequest(TransactionRequest req) {
        if (req.transactionId() == null || req.transactionId().isBlank())
            throw new IllegalArgumentException("transactionId is required");
        if (req.accountId() == null || req.accountId().isBlank())
            throw new IllegalArgumentException("accountId is required");
        if (req.amount() <= 0)
            throw new IllegalArgumentException("amount must be positive");
    }
}
