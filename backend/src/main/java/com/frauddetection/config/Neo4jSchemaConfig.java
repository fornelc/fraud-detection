package com.frauddetection.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class Neo4jSchemaConfig {

    private final Driver driver;

    @PostConstruct
    public void initConstraints() {
        log.info("Initializing Neo4j schema constraints...");
        try (Session session = driver.session()) {
            session.run("CREATE CONSTRAINT account_id IF NOT EXISTS FOR (a:Account) REQUIRE a.accountId IS UNIQUE");
            session.run("CREATE CONSTRAINT transaction_id IF NOT EXISTS FOR (t:Transaction) REQUIRE t.transactionId IS UNIQUE");
            session.run("CREATE CONSTRAINT device_id IF NOT EXISTS FOR (d:Device) REQUIRE d.deviceId IS UNIQUE");
            session.run("CREATE CONSTRAINT ip_address IF NOT EXISTS FOR (ip:IpAddress) REQUIRE ip.address IS UNIQUE");
            session.run("CREATE CONSTRAINT merchant_id IF NOT EXISTS FOR (m:Merchant) REQUIRE m.merchantId IS UNIQUE");

            // Property existence constraints for critical fields
            session.run("CREATE INDEX account_risk_score IF NOT EXISTS FOR (a:Account) ON (a.riskScore)");
            session.run("CREATE INDEX account_blacklisted IF NOT EXISTS FOR (a:Account) ON (a.blacklisted)");
            session.run("CREATE INDEX transaction_flagged IF NOT EXISTS FOR (t:Transaction) ON (t.flagged)");
            session.run("CREATE INDEX transaction_timestamp IF NOT EXISTS FOR (t:Transaction) ON (t.timestamp)");
            session.run("CREATE INDEX alert_timestamp IF NOT EXISTS FOR (a:Alert) ON (a.timestamp)");
        }
        log.info("Neo4j schema constraints initialized successfully");
    }
}
