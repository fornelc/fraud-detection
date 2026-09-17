package com.frauddetection.service;

import com.frauddetection.dto.FraudRingResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final Neo4jClient neo4jClient;

    @CircuitBreaker(name = "neo4j")
    public List<FraudRingResponse> findFraudRings() {
        // Combines two independent signals: shared-device rings and shared-IP rings.
        List<FraudRingResponse> rings = findSharedDeviceRings();
        mergeSharedIpRings(rings);
        return rings;
    }

    // Finds account pairs whose transactions used the same Device — a fraud signal on its
    // own, independent of any blacklist. Two unrelated legitimate accounts converging on
    // the exact same device fingerprint is unusual enough to be worth surfacing by itself.
    private List<FraudRingResponse> findSharedDeviceRings() {
        List<FraudRingResponse> rings = new ArrayList<>();

        neo4jClient.query("""
            MATCH (a1:Account)-[:MADE]->(:Transaction)-[:USED]->(d:Device)
                  <-[:USED]-(:Transaction)<-[:MADE]-(a2:Account)
            WHERE a1.accountId < a2.accountId
            WITH a1.accountId AS acc1, a2.accountId AS acc2,
                 collect(DISTINCT d.deviceId) AS devices,
                 count(*) AS txCount
            RETURN acc1, acc2, devices, txCount
            ORDER BY txCount DESC
            """)
            .fetch()
            .all()
            .forEach(row -> rings.add(new FraudRingResponse(
                (String) row.get("acc1"),
                (String) row.get("acc2"),
                toStringList(row.get("devices")),
                List.of(),
                ((Number) row.get("txCount")).longValue()
            )));

        return rings;
    }

    // Finds account pairs whose transactions used the same IP — a second, independent fraud
    // signal alongside shared devices. Mutates the given list in place: a pair already found
    // via findSharedDeviceRings() gets its IPs merged in, so a pair sharing both shows up as
    // one combined ring entry rather than two separate ones.
    private void mergeSharedIpRings(List<FraudRingResponse> rings) {
        neo4jClient.query("""
            MATCH (a1:Account)-[:MADE]->(:Transaction)-[:FROM]->(ip:IpAddress)
                  <-[:FROM]-(:Transaction)<-[:MADE]-(a2:Account)
            WHERE a1.accountId < a2.accountId
            WITH a1.accountId AS acc1, a2.accountId AS acc2,
                 collect(DISTINCT ip.address) AS ips,
                 count(*) AS txCount
            RETURN acc1, acc2, ips, txCount
            ORDER BY txCount DESC
            """)
            .fetch()
            .all()
            .forEach(row -> {
                String acc1 = (String) row.get("acc1");
                String acc2 = (String) row.get("acc2");
                List<String> ips = toStringList(row.get("ips"));
                long txCount = ((Number) row.get("txCount")).longValue();

                // Merge with existing device-ring entry if present
                rings.stream()
                    .filter(r -> r.account1Id().equals(acc1) && r.account2Id().equals(acc2))
                    .findFirst()
                    .ifPresentOrElse(
                        existing -> rings.set(rings.indexOf(existing),
                            new FraudRingResponse(acc1, acc2, existing.sharedDevices(), ips,
                                Math.max(existing.sharedTransactionCount(), txCount))),
                        () -> rings.add(new FraudRingResponse(acc1, acc2, List.of(), ips, txCount))
                    );
            });
    }

    private List<String> toStringList(Object value) {
        // Element type is erased by Map<String,Object>, so List<?> is the only legal check —
        // mapping to toString() avoids an unchecked cast to List<String> we couldn't verify anyway.
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

}
