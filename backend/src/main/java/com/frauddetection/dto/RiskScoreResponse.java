package com.frauddetection.dto;

public record RiskScoreResponse(
    String accountId,
    double riskScore,
    int minDistanceToBlacklisted,
    boolean blacklisted,
    String riskLevel
) {
    public static String resolveLevel(double score) {
        if (score >= 0.7) return "HIGH";
        if (score >= 0.4) return "MEDIUM";
        return "LOW";
    }
}
