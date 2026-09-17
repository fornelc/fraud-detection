package com.frauddetection.controller;

import com.frauddetection.domain.Account;
import com.frauddetection.dto.FraudRingResponse;
import com.frauddetection.dto.RiskScoreResponse;
import com.frauddetection.repository.AccountRepository;
import com.frauddetection.service.FraudDetectionService;
import com.frauddetection.service.RiskScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for fraud detection: shared-infrastructure ring detection, graph-distance
 * risk scoring, and the manual blacklist action. Backed by {@link FraudDetectionService},
 * {@link RiskScoringService}, and {@link AccountRepository}.
 */
@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final FraudDetectionService fraudDetectionService;
    private final RiskScoringService riskScoringService;
    private final AccountRepository accountRepository;

    /**
     * Finds every pair of accounts whose transactions shared a Device or IP address —
     * independent of blacklist status. See {@link FraudDetectionService#findFraudRings}.
     *
     * @return detected ring pairs, most shared transactions first
     */
    @GetMapping("/rings")
    public ResponseEntity<List<FraudRingResponse>> getFraudRings() {
        return ResponseEntity.ok(fraudDetectionService.findFraudRings());
    }

    /**
     * Computes one account's risk score from its graph distance to the nearest blacklisted
     * account. See {@link RiskScoringService#calculateRiskScore} for the decay formula.
     *
     * @param accountId the account to score
     * @return the computed score, distance to nearest blacklisted account, and risk level
     */
    @GetMapping("/accounts/{accountId}/risk-score")
    public ResponseEntity<RiskScoreResponse> getRiskScore(@PathVariable String accountId) {
        return ResponseEntity.ok(riskScoringService.calculateRiskScore(accountId));
    }

    /**
     * Manually marks an account as a confirmed bad actor — the only way an account ever
     * becomes blacklisted. Deliberately not automatic: risk scoring is derived from blacklist
     * status, so letting a high score auto-blacklist would create a runaway feedback loop.
     *
     * @param accountId the account to blacklist
     */
    @PostMapping("/accounts/{accountId}/blacklist")
    public ResponseEntity<Void> blacklist(@PathVariable String accountId) {
        accountRepository.blacklistAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns every account currently marked as blacklisted.
     *
     * @return all blacklisted accounts
     */
    @GetMapping("/accounts/blacklisted")
    public ResponseEntity<List<Account>> getBlacklisted() {
        return ResponseEntity.ok(accountRepository.findByBlacklistedTrue());
    }
}
