package com.frauddetection.repository;

import com.frauddetection.domain.Transaction;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface TransactionRepository extends Neo4jRepository<Transaction, String> {

    @Query("""
        MATCH (t:Transaction {flagged: true})
        RETURN t
        ORDER BY t.timestamp DESC
        LIMIT 100
        """)
    List<Transaction> findFlaggedTransactions();

    @Query("""
        MATCH (a:Account {accountId: $accountId})-[:MADE]->(t:Transaction)
        RETURN t
        ORDER BY t.timestamp DESC
        LIMIT 50
        """)
    List<Transaction> findByAccountId(String accountId);
}
