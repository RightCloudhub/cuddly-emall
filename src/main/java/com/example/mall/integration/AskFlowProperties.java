package com.example.mall.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the AskFlow integration. Bound from {@code mall.askflow.*} keys in {@code
 * application.yml}.
 *
 * <ul>
 *   <li>{@code base-url} — root of the AskFlow service (e.g. {@code http://askflow:8000}).
 *   <li>{@code service-token} — long-lived bearer presented as {@code Authorization: Bearer ...} on
 *       requests to AskFlow AND expected on inbound integration endpoints (order lookup, ticket
 *       callback, loyalty). Same value on both sides — service-to-service.
 *   <li>{@code kb-max-retries} / {@code user-max-retries} — outbox attempts before {@code dead}.
 *   <li>{@code batch-size} — rows per worker tick.
 * </ul>
 */
@ConfigurationProperties(prefix = "mall.askflow")
public class AskFlowProperties {

    private String baseUrl = "http://askflow:8000";
    private String serviceToken = "";
    private int kbMaxRetries = 5;
    private int userMaxRetries = 5;
    private int batchSize = 100;
    private int requestTimeoutMs = 10_000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken == null ? "" : serviceToken.trim();
    }

    public int getKbMaxRetries() {
        return kbMaxRetries;
    }

    public void setKbMaxRetries(int kbMaxRetries) {
        this.kbMaxRetries = kbMaxRetries;
    }

    public int getUserMaxRetries() {
        return userMaxRetries;
    }

    public void setUserMaxRetries(int userMaxRetries) {
        this.userMaxRetries = userMaxRetries;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }
}
