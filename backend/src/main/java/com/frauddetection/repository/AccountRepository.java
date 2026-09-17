package com.frauddetection.repository;

import com.frauddetection.domain.Account;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AccountRepository extends Neo4jRepository<Account, String> {

    List<Account> findByBlacklistedTrue();

    @Query("""
        MATCH (a:Account {accountId: $accountId})
        SET a.riskScore = $riskScore
        RETURN a
        """)
    Account updateRiskScore(@Param("accountId") String accountId, @Param("riskScore") double riskScore);

    @Query("""
        MATCH (a:Account {accountId: $accountId})
        SET a.blacklisted = true, a.riskScore = 1.0
        RETURN a
        """)
    Account blacklistAccount(@Param("accountId") String accountId);
}
