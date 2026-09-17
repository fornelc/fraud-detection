#!/usr/bin/env bash
#
# Seeds the demo story used in PRESENTATION.md: one blacklisted "hub" account,
# a 2-hop chain off it (HIGH -> MEDIUM decay), a direct-IP link (HIGH), an
# isolated control transaction (LOW), and two independent fraud rings
# (one linked by shared device, one by shared IP).
#
# Requires: backend running on localhost:8080, Neo4j reachable. curl + jq.
#
# Usage: ./scripts/ingest-demo.sh

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

ingest() {
    local tx_id=$1 acc_id=$2 amount=$3 device_id=$4 ip=$5 merchant_id=$6

    echo "--- Ingesting $tx_id (account $acc_id) ---"
    curl -sS -X POST "$BASE_URL/api/transactions" \
        -H "Content-Type: application/json" \
        -d @- <<JSON | jq '{transactionId, accountId, riskScore, flagged, status}'
{
  "transactionId": "$tx_id",
  "accountId": "$acc_id",
  "accountName": "User $acc_id",
  "accountEmail": "$(echo "$acc_id" | tr '[:upper:]' '[:lower:]')@test.com",
  "amount": $amount,
  "deviceId": "$device_id",
  "deviceType": "mobile",
  "deviceFingerprint": "fp-$device_id",
  "ipAddress": "$ip",
  "ipCountry": "US",
  "merchantId": "$merchant_id",
  "merchantName": "Merchant $merchant_id",
  "merchantCategory": "retail"
}
JSON
    echo
}

blacklist() {
    local acc_id=$1
    echo "--- Blacklisting $acc_id ---"
    curl -sS -X POST "$BASE_URL/api/fraud/accounts/$acc_id/blacklist" -w "HTTP %{http_code}\n" -o /dev/null
    echo
}

# 1. Seed the hub account, then blacklist it before ingesting anything linked to it.
ingest "TX-1001" "ACC-100" 500 "DEV-100" "203.0.113.10" "MER-01"
blacklist "ACC-100"

# 2. Direct device link to the blacklisted hub -> expect HIGH (0.70), flagged=true.
ingest "TX-1002" "ACC-101" 320 "DEV-100" "203.0.113.20" "MER-02"

# 3. Same account, new device -> bridge for the 2-hop chain below.
ingest "TX-1003" "ACC-101" 275 "DEV-101" "203.0.113.20" "MER-02"

# 4. Two hops from the hub via ACC-101's bridge device -> expect MEDIUM (0.40).
ingest "TX-1004" "ACC-102" 410 "DEV-101" "203.0.113.30" "MER-03"

# 5. Direct IP link to the blacklisted hub -> expect HIGH (0.70), flagged=true.
ingest "TX-1005" "ACC-103" 600 "DEV-102" "203.0.113.10" "MER-01"

# 6. Fully isolated control transaction -> expect LOW (0.0).
ingest "TX-1006" "ACC-104" 90 "DEV-103" "203.0.113.40" "MER-04"

# 7-8. Independent ring, linked by shared device (unrelated to the blacklist).
ingest "TX-1007" "ACC-105" 150 "DEV-104" "203.0.113.50" "MER-05"
ingest "TX-1008" "ACC-106" 165 "DEV-104" "203.0.113.60" "MER-05"

# 9-10. Independent ring, linked by shared IP (unrelated to the blacklist).
ingest "TX-1009" "ACC-107" 200 "DEV-105" "203.0.113.70" "MER-06"
ingest "TX-1010" "ACC-108" 220 "DEV-106" "203.0.113.70" "MER-06"

# 11-12. Extends the chain one more bridge past ACC-102 (12 real hops from ACC-100).
# RiskScoringService caps shortestPath at [*..8], so this account comes back
# indistinguishable from "no connection" (-1, 0.0, LOW) -- a real false negative
# at the search boundary, not a bug. See DEMO-DATA-EXPLAINED.md.
ingest "TX-1011" "ACC-102" 300 "DEV-107" "203.0.113.30" "MER-03"
ingest "TX-1012" "ACC-109" 260 "DEV-107" "203.0.113.100" "MER-07"

echo "Done. Check the Rings tab for the two independent pairs, and Overview for the two live alerts."
