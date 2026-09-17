# Financial Fraud Detection & Network Security Engine

A real-time fraud detection system built on a **graph database (Neo4j)** to expose hidden relationships between users, devices, transactions, and merchants, relationships that SQL databases struggle to query efficiently.

---

## Why a Graph Database?

Traditional SQL databases store rows. Neo4j stores **nodes and relationships**, making it trivial to answer questions like:

> *"Which accounts have transacted from the same device as a known fraudster, within 4 degrees of separation?"*

That query in SQL requires multiple JOINs across millions of rows. In Neo4j it is a single `shortestPath` Cypher statement that runs in milliseconds.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17 + Spring Boot 3.3.5 |
| Graph Database | Neo4j 5 (Spring Data Neo4j) |
| Circuit Breaker | Resilience4j 2.2.0 |
| Live Alerts | Server-Sent Events (SSE) |
| Frontend | React 18 + Vite |
| Testing | JUnit 5 + Testcontainers |
| Infrastructure | Docker + Docker Compose |

---

## Prerequisites

- **Docker Desktop** running
- **Java 17+** (`java -version`)
- **Maven 3.9+** (`mvn -version`)
- **Node.js 20+** (`node -version`)

---

## Running the Project

### Step 1 — Start Neo4j

```bash
docker-compose up neo4j
```

Wait until the logs show `Started`. Neo4j browser is available at `http://localhost:7474`  
Login: `neo4j` / `password123`

### Step 2 — Start the Backend

Open `backend/src/main/java/com/frauddetection/FraudDetectionApplication.java` in IntelliJ and click the green ▶ button next to `main`.

Or from a terminal:

```bash
cd backend
mvn spring-boot:run
```

Wait for `Started FraudDetectionApplication` in the logs. API runs on `http://localhost:8080`.

### Step 3 — Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

Dashboard runs on `http://localhost:5173`.

### Run Everything with Docker (alternative)

```bash
docker-compose up --build
```

Frontend at `http://localhost:80`, backend at `http://localhost:8080`.

---

## Running the Tests

Tests spin up a real Neo4j instance automatically via Testcontainers, no manual database setup needed. Docker must be running.

```bash
cd backend
mvn test
```

---

## API Reference

### POST `/api/transactions`
Ingests a transaction and links all entities into the graph. Automatically calculates the account's risk score and flags the transaction if the score exceeds 0.7.

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "tx-001",
    "accountId": "acc-001",
    "accountName": "Alice",
    "accountEmail": "alice@example.com",
    "amount": 500.00,
    "deviceId": "dev-001",
    "deviceType": "mobile",
    "deviceFingerprint": "fp-abc123",
    "ipAddress": "192.168.1.1",
    "ipCountry": "US",
    "merchantId": "mer-001",
    "merchantName": "Amazon",
    "merchantCategory": "e-commerce"
  }'
```

### GET `/api/fraud/rings`
Returns all pairs of accounts that share a device or IP address, the signature of an identity fraud ring.

### GET `/api/fraud/accounts/{accountId}/risk-score`
Returns the dynamic risk score for an account based on its graph distance from blacklisted accounts.

### POST `/api/fraud/accounts/{accountId}/blacklist`
Marks an account as blacklisted (risk score = 1.0). All accounts connected to it will see their scores rise on next calculation.

### GET `/api/alerts/stream`
SSE endpoint. Connect to receive real-time alerts whenever a high-risk transaction is flagged. The frontend connects here automatically.

### GET `/api/transactions/flagged`
Returns the last 100 flagged transactions.

---

## Graph Data Model

```
(Account)-[:MADE]->(Transaction)-[:TO]----->(Merchant)
                               |-[:USED]-->(Device)
                               └-[:FROM]->(IpAddress)
```

**Fraud ring pattern**, two accounts share the same device:
```
Account_A -[:MADE]-> Tx_1 -[:USED]-> Device_X <-[:USED]- Tx_2 <-[:MADE]- Account_B
```

This 4-hop path is what the fraud detection queries traverse. The shorter the path to a blacklisted account, the higher the risk score.

---

## Risk Score Formula

| Distance to blacklisted account | Risk Score |
|---|---|
| 0 (account is itself blacklisted) | 1.00 |
| 2 hops | 0.85 |
| 4 hops (share device/IP) | 0.70 → flagged |
| 6 hops | 0.55 |
| 8 hops | 0.40 |
| No connection found | 0.00 |

Transactions are automatically flagged when risk score ≥ **0.70**.

---

## Project Structure

```
fraud-detection/
├── docker-compose.yml          # Neo4j + backend + frontend orchestration
├── backend/
│   ├── pom.xml                 # Maven dependencies
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/frauddetection/
│       │   │   ├── FraudDetectionApplication.java   # Spring Boot entry point
│       │   │   │
│       │   │   ├── config/
│       │   │   │   ├── Neo4jSchemaConfig.java       # Creates uniqueness constraints + indexes on startup
│       │   │   │   └── CorsConfig.java              # Allows frontend (port 5173) to call the API
│       │   │   │
│       │   │   ├── domain/                          # Neo4j node entities (@Node)
│       │   │   │   ├── Account.java                 # accountId, name, email, riskScore, blacklisted
│       │   │   │   ├── Transaction.java             # transactionId, amount, timestamp, flagged, status
│       │   │   │   ├── Device.java                  # deviceId, type, fingerprint
│       │   │   │   ├── IpAddress.java               # address, country, suspicious
│       │   │   │   └── Merchant.java                # merchantId, name, category, flagged
│       │   │   │
│       │   │   ├── repository/                      # Spring Data Neo4j repositories
│       │   │   │   ├── AccountRepository.java       # findByBlacklistedTrue, updateRiskScore, blacklistAccount
│       │   │   │   ├── TransactionRepository.java   # findFlaggedTransactions, findByAccountId
│       │   │   │   ├── DeviceRepository.java
│       │   │   │   ├── IpAddressRepository.java
│       │   │   │   └── MerchantRepository.java
│       │   │   │
│       │   │   ├── dto/                             # Input/output shapes (Java records)
│       │   │   │   ├── TransactionRequest.java      # Payload for POST /api/transactions
│       │   │   │   ├── TransactionResponse.java     # Returned after ingestion
│       │   │   │   ├── RiskScoreResponse.java       # Score, level, distance, blacklisted flag
│       │   │   │   ├── FraudRingResponse.java       # Two accounts + shared devices/IPs
│       │   │   │   └── AlertEvent.java              # Pushed over SSE when a transaction is flagged
│       │   │   │
│       │   │   ├── service/
│       │   │   │   ├── TransactionIngestionService.java  # Core ingestion: single MERGE Cypher query
│       │   │   │   │                                     # links all entities, then scores and flags
│       │   │   │   ├── FraudDetectionService.java        # Finds account pairs sharing devices or IPs
│       │   │   │   │                                     # via native Cypher traversal (no N+1)
│       │   │   │   ├── RiskScoringService.java           # shortestPath query to blacklisted accounts,
│       │   │   │   │                                     # returns score 0.0–1.0
│       │   │   │   └── AlertBroadcastService.java        # Manages SSE emitters, broadcasts alerts
│       │   │   │                                         # to all connected dashboard clients
│       │   │   │
│       │   │   ├── controller/
│       │   │   │   ├── TransactionController.java   # POST /api/transactions, GET flagged/by-account
│       │   │   │   ├── FraudController.java         # GET /rings, GET /risk-score, POST /blacklist
│       │   │   │   └── AlertController.java         # GET /api/alerts/stream (SSE)
│       │   │   │
│       │   │   └── exception/
│       │   │       ├── ServiceUnavailableException.java  # Thrown by circuit breaker fallbacks
│       │   │       └── GlobalExceptionHandler.java       # Maps exceptions to clean JSON error responses
│       │   │
│       │   └── resources/
│       │       └── application.yml                  # Neo4j URI, credentials, Resilience4j config
│       │
│       └── test/java/com/frauddetection/
│           ├── TransactionIngestionServiceTest.java  # Tests ingestion, graph creation, validation
│           └── FraudDetectionServiceTest.java        # Tests fraud ring detection and risk scoring
│
└── frontend/
    ├── package.json
    ├── vite.config.js              # Dev server on 5173, proxies /api to :8080
    ├── nginx.conf                  # Production: proxies /api to backend container
    ├── index.html
    └── src/
        ├── main.jsx                # React entry point
        ├── App.jsx                 # Root component, tabs, SSE connection, ingest form
        ├── services/
        │   ├── api.js              # axios wrapper for all REST calls
        │   └── alertStream.js      # EventSource wrapper for SSE alerts
        └── components/
            ├── AlertPanel.jsx      # Live alert feed, updates in real time via SSE
            ├── RiskScoreCard.jsx   # Look up any account's risk score, blacklist button
            ├── TransactionList.jsx # Table of all flagged transactions
            └── FraudRings.jsx      # Cards showing account pairs in detected fraud rings
```

---

## Key Design Decisions

**Single MERGE query for ingestion**
Instead of loading objects into Java memory and saving them one by one, `TransactionIngestionService` sends one Cypher `MERGE` statement that creates all five nodes and four relationships atomically. This avoids the N+1 problem entirely.

**Neo4jClient for complex traversals**
Fraud ring detection and risk scoring bypass the Spring Data object-mapping layer and use `Neo4jClient` directly. This returns only the projections needed (account IDs, distances) rather than loading full entity graphs.

**Circuit breaker on every DB call**
Every service method is annotated with `@CircuitBreaker(name = "neo4j")`. If Neo4j becomes unreachable, the circuit opens after 5 failures and returns a `503` immediately instead of letting threads pile up on a dead connection.

**Schema constraints at startup**
`Neo4jSchemaConfig` creates uniqueness constraints for all node IDs before any request is served. This prevents duplicate nodes from concurrent ingestion and acts as the database guardrail the graph needs.

**SSE over WebSockets**
Server-Sent Events are used for alert streaming because the communication is one-directional (server → client only). SSE is simpler, reconnects automatically, and works over plain HTTP/1.1 without a protocol upgrade.

