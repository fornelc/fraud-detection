package com.frauddetection;

import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.dto.TransactionResponse;
import com.frauddetection.service.TransactionIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class TransactionIngestionServiceTest {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5")
            .withAdminPassword("password123");

    @DynamicPropertySource
    static void registerNeo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", neo4j::getAdminPassword);
    }

    @Autowired
    private TransactionIngestionService ingestionService;

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void cleanup() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    void shouldIngestTransactionAndCreateGraphRelationships() {
        TransactionRequest request = buildRequest("tx-001", "acc-001", "dev-001", "192.168.1.1", "mer-001");

        TransactionResponse response = ingestionService.ingest(request);

        assertThat(response.transactionId()).isEqualTo("tx-001");
        assertThat(response.accountId()).isEqualTo("acc-001");
        assertThat(response.flagged()).isFalse();
        assertThat(response.status()).isEqualTo("COMPLETED");

        // Verify graph structure in DB
        long txCount = neo4jClient.query("""
            MATCH (a:Account {accountId: 'acc-001'})-[:MADE]->(t:Transaction {transactionId: 'tx-001'})
                  -[:USED]->(d:Device {deviceId: 'dev-001'})
            RETURN count(t) AS cnt
            """).fetchAs(Long.class).mappedBy((ts, r) -> r.get("cnt").asLong()).one().orElse(0L);

        assertThat(txCount).isEqualTo(1);
    }

    @Test
    void shouldFlagHighRiskTransactionWhenAccountIsBlacklisted() {
        // First, create a blacklisted account that shares a device
        neo4jClient.query("""
            CREATE (bad:Account {accountId:'bad-001', name:'Fraudster', email:'bad@x.com',
                                 riskScore:1.0, blacklisted:true})
            CREATE (d:Device {deviceId:'shared-dev', type:'mobile', fingerprint:'fp-999'})
            CREATE (t1:Transaction {transactionId:'old-tx', amount:500, timestamp:'2024-01-01T00:00:00',
                                    flagged:true, status:'FLAGGED'})
            CREATE (bad)-[:MADE]->(t1)-[:USED]->(d)
            """).run();

        // Now ingest a transaction from a new account using the same shared device — 4 hops:
        // acc-new -MADE-> tx-new -USED-> shared-dev <-USED- old-tx <-MADE- bad-001 (blacklisted)
        TransactionRequest request = buildRequest("tx-new", "acc-new", "shared-dev", "10.0.0.1", "mer-002");
        TransactionResponse response = ingestionService.ingest(request);

        // Exact decay formula at 4 hops: 1.0 - (4 * 0.075) = 0.70 — crosses the 0.7 HIGH_RISK_THRESHOLD.
        assertThat(response.riskScore()).isEqualTo(0.70);
        assertThat(response.flagged()).isTrue();
        assertThat(response.status()).isEqualTo("FLAGGED");
    }

    @Test
    void shouldRejectRequestWithBlankTransactionId() {
        TransactionRequest bad = new TransactionRequest(
                "", "acc-001", "John", "j@x.com", 100, "dev-001", "mobile", "fp", "1.1.1.1", "US", "mer-001", "Shop", "retail"
        );
        assertThatThrownBy(() -> ingestionService.ingest(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transactionId");

        // validateRequest() runs before writeSubgraph(), so a rejected request must leave zero
        // trace in the graph — not even the Account node should have been MERGEd.
        assertThat(countAccountNodes("acc-001")).isZero();
    }

    @Test
    void shouldRejectRequestWithNonPositiveAmount() {
        TransactionRequest bad = new TransactionRequest(
                "tx-x", "acc-001", "John", "j@x.com", -50, "dev-001", "mobile", "fp", "1.1.1.1", "US", "mer-001", "Shop", "retail"
        );
        assertThatThrownBy(() -> ingestionService.ingest(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");

        // Same guarantee as above: validation fails before any Neo4j write is attempted.
        assertThat(countAccountNodes("acc-001")).isZero();
    }

    private long countAccountNodes(String accountId) {
        return neo4jClient.query("""
                MATCH (a:Account {accountId: $accountId})
                RETURN count(a) AS cnt
                """)
                .bind(accountId).to("accountId")
                .fetchAs(Long.class)
                .mappedBy((ts, r) -> r.get("cnt").asLong())
                .one()
                .orElse(0L);
    }

    private TransactionRequest buildRequest(String txId, String accId, String devId, String ip, String merId) {
        return new TransactionRequest(
                txId, accId, "John Doe", "john@example.com",
                250.0,
                devId, "mobile", "fp-abc123",
                ip, "US",
                merId, "Amazon", "e-commerce"
        );
    }
}
