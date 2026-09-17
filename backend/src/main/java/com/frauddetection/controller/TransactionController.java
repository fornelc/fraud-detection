package com.frauddetection.controller;

import com.frauddetection.domain.Transaction;
import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.dto.TransactionResponse;
import com.frauddetection.repository.TransactionRepository;
import com.frauddetection.service.TransactionIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for transaction ingestion and lookup. The POST endpoint is the single entry
 * point into the whole fraud-detection pipeline — see {@link TransactionIngestionService}.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionIngestionService ingestionService;
    private final TransactionRepository transactionRepository;

    /**
     * Ingests one transaction: builds its subgraph, scores the account against known fraud,
     * and flags + alerts if the score crosses the risk threshold. See
     * {@link TransactionIngestionService#ingest} for the full pipeline.
     *
     * @param request the transaction and its account/device/IP/merchant details
     * @return the ingestion result, including the computed risk score and flagged status
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> ingest(@RequestBody TransactionRequest request) {
        TransactionResponse response = ingestionService.ingest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns transactions currently flagged as high-risk, most recent first.
     *
     * @return up to 100 flagged transactions
     */
    @GetMapping("/flagged")
    public ResponseEntity<List<Transaction>> getFlagged() {
        return ResponseEntity.ok(transactionRepository.findFlaggedTransactions());
    }

    /**
     * Returns one account's transaction history, most recent first.
     *
     * @param accountId the account to look up
     * @return up to 50 of that account's transactions
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Transaction>> getByAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(transactionRepository.findByAccountId(accountId));
    }
}
