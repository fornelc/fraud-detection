package com.frauddetection.service;

import com.frauddetection.dto.RiskScoreResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoringService {

    private final Neo4jClient neo4jClient;

    // Orchestrates one risk-score computation: fetch the account's blacklist status and its
    // distance to the nearest blacklisted account in a single query, run the pure decay
    // formula on the result, then build the response DTO.
    @CircuitBreaker(name = "neo4j")
    public RiskScoreResponse calculateRiskScore(String accountId) {
        Map<String, Object> row = fetchAccountRiskRow(accountId);

        boolean blacklisted = Boolean.TRUE.equals(row.get("blacklisted"));
        long minDist = ((Number) row.get("minDistance")).longValue();
        double riskScore = computeRiskScore(blacklisted, minDist);

        return new RiskScoreResponse(
            accountId,
            riskScore,
            (int) minDist,
            blacklisted,
            RiskScoreResponse.resolveLevel(riskScore)
        );
    }

    // Single Cypher query: checks whether the account itself is blacklisted, and finds the
    // shortest path to the nearest blacklisted account, in one round trip. OPTIONAL MATCH is
    // what lets "account exists but has no nearby fraud" come back as a real row with
    // minDistance = -1, instead of no row at all — which is why a missing row here means the
    // account genuinely doesn't exist, not just that it has no risk.
    private Map<String, Object> fetchAccountRiskRow(String accountId) {
        Optional<Map<String, Object>> result = neo4jClient.query("""
            MATCH (target:Account {accountId: $accountId})
            OPTIONAL MATCH path = shortestPath(
                (target)-[*..8]-(blacklisted:Account {blacklisted: true})
            )
            WHERE target <> blacklisted
            WITH target,
                 CASE WHEN target.blacklisted THEN 0
                      WHEN path IS NOT NULL THEN length(path)
                      ELSE -1
                 END AS minDist
            RETURN target.accountId        AS accountId,
                   target.blacklisted      AS blacklisted,
                   target.riskScore        AS currentScore,
                   minDist                 AS minDistance
            """)
            .bind(accountId).to("accountId")
            .fetch()
            .first();

        if (result.isEmpty()) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        return result.get();
    }

    // Pure decay calculation, no I/O: 0 hops (is the fraudster) = 1.0, further away decays
    // linearly, no connection found (-1) = 0.0. See DEMO-DATA-EXPLAINED.md for why the
    // Math.max(0.0, ...) guard is currently unreachable given today's [*..8] cap, but still
    // worth keeping as a safety net if that cap or the 0.075 coefficient ever change alone.
    private double computeRiskScore(boolean blacklisted, long minDist) {
        if (blacklisted || minDist == 0) {
            return 1.0;
        } else if (minDist < 0) {
            return 0.0;
        } else {
            // Decay: distance=2 → 0.85, distance=4 → 0.70, distance=6 → 0.55, distance=8 → 0.40
            return Math.max(0.0, 1.0 - (minDist * 0.075));
        }
    }

}
