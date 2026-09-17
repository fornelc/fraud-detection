package com.frauddetection;

import com.frauddetection.dto.FraudRingResponse;
import com.frauddetection.dto.RiskScoreResponse;
import com.frauddetection.service.FraudDetectionService;
import com.frauddetection.service.RiskScoringService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class FraudDetectionServiceTest {

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
    private FraudDetectionService fraudDetectionService;

    @Autowired
    private RiskScoringService riskScoringService;

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void seedGraph() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        // Two accounts sharing the same device — a classic identity fraud ring
        neo4jClient.query("""
            CREATE (a1:Account {accountId:'acc-A', name:'Alice', email:'a@x.com', riskScore:0.0, blacklisted:false})
            CREATE (a2:Account {accountId:'acc-B', name:'Bob',   email:'b@x.com', riskScore:0.0, blacklisted:false})
            CREATE (bl:Account {accountId:'acc-BAD', name:'Fraudster', email:'bad@x.com', riskScore:1.0, blacklisted:true})
            CREATE (d1:Device  {deviceId:'shared-device', type:'desktop', fingerprint:'fp-X'})
            CREATE (m1:Merchant {merchantId:'mer-1', name:'Shop', category:'retail', flagged:false})
            CREATE (ip1:IpAddress {address:'10.0.0.1', country:'US', suspicious:false})

            CREATE (t1:Transaction {transactionId:'tx-A1', amount:100, timestamp:'2024-01-01T00:00:00', flagged:false, status:'COMPLETED'})
            CREATE (t2:Transaction {transactionId:'tx-B1', amount:200, timestamp:'2024-01-02T00:00:00', flagged:false, status:'COMPLETED'})
            CREATE (t3:Transaction {transactionId:'tx-BAD', amount:999, timestamp:'2024-01-03T00:00:00', flagged:true, status:'FLAGGED'})

            CREATE (a1)-[:MADE]->(t1)-[:USED]->(d1)
            CREATE (a2)-[:MADE]->(t2)-[:USED]->(d1)
            CREATE (bl)-[:MADE]->(t3)-[:USED]->(d1)
            CREATE (t1)-[:TO]->(m1)
            CREATE (t2)-[:TO]->(m1)
            CREATE (t3)-[:TO]->(m1)
            CREATE (t1)-[:FROM]->(ip1)
            CREATE (t2)-[:FROM]->(ip1)
            CREATE (t3)-[:FROM]->(ip1)
            """).run();
    }

    @Test
    void shouldDetectFraudRingWhenAccountsShareDevice() {
        // acc-A and acc-B each made their own transaction, but both transactions used the same
        // shared-device: acc-A -MADE-> tx-A1 -USED-> shared-device <-USED- tx-B1 <-MADE- acc-B.
        // That's the exact pattern findSharedDeviceRings() matches — completely independent of
        // acc-BAD/blacklist status, which is why this ring is found even though neither acc-A
        // nor acc-B is anywhere near the blacklisted account in the other test below.
        List<FraudRingResponse> rings = fraudDetectionService.findFraudRings();

        assertThat(rings).isNotEmpty();
        boolean pairFound = rings.stream().anyMatch(r ->
            (r.account1Id().equals("acc-A") || r.account2Id().equals("acc-A")) &&
            (r.account1Id().equals("acc-B") || r.account2Id().equals("acc-B"))
        );
        assertThat(pairFound).isTrue();
    }

    @Test
    void shouldCalculateHighRiskScoreForAccountNearBlacklisted() {
        // acc-A shares a device with acc-BAD (blacklisted) — 4 hops: acc-A -MADE-> tx-A1 -USED-> device <-USED- tx-BAD <-MADE- acc-BAD
        RiskScoreResponse score = riskScoringService.calculateRiskScore("acc-A");

        // Exact decay formula at 4 hops: 1.0 - (4 * 0.075) = 0.70 — HIGH, not just "elevated."
        assertThat(score.riskScore()).isEqualTo(0.70);
        assertThat(score.riskLevel()).isEqualTo("HIGH");
        assertThat(score.minDistanceToBlacklisted()).isEqualTo(4);
        assertThat(score.blacklisted()).isFalse();
    }

    @Test
    void shouldReturnFullRiskScoreForBlacklistedAccount() {
        RiskScoreResponse score = riskScoringService.calculateRiskScore("acc-BAD");

        assertThat(score.riskScore()).isEqualTo(1.0);
        assertThat(score.blacklisted()).isTrue();
        assertThat(score.riskLevel()).isEqualTo("HIGH");
    }
}
