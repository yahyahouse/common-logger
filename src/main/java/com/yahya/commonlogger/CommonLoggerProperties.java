package com.yahya.commonlogger;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.logging.LogLevel;

@ConfigurationProperties(prefix = "common.logger")
public class CommonLoggerProperties {
    private String correlationIdHeader = "X-Correlation-Id";
    private String correlationIdMdcKey = "correlationId";

    /**
     * Log level used for successful method executions.
     */
    private LogLevel logLevel = LogLevel.INFO;

    /**
     * Identifier for the API / use case being logged.
     */
    private String apiId = "";

    /**
     * HTTP status code to emit for successful executions.
     */
    private int successHttpStatusCode = 200;

    /**
     * HTTP status code to emit for failed executions.
     */
    private int errorHttpStatusCode = 500;

    /**
     * MDC key used to resolve the transaction identifier (defaults to correlationIdMdcKey).
     */
    private String transactionIdMdcKey;

    /**
     * MDC key used to resolve the internal transaction identifier (defaults to correlationIdMdcKey).
     */
    private String internalTransactionIdMdcKey;

    public String getCorrelationIdHeader() {
        return correlationIdHeader;
    }

    public void setCorrelationIdHeader(String correlationIdHeader) {
        this.correlationIdHeader = correlationIdHeader;
    }

    public String getCorrelationIdMdcKey() {
        return correlationIdMdcKey;
    }

    public void setCorrelationIdMdcKey(String correlationIdMdcKey) {
        this.correlationIdMdcKey = correlationIdMdcKey;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel == null ? LogLevel.INFO : logLevel;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId == null ? "" : apiId;
    }

    public int getSuccessHttpStatusCode() {
        return successHttpStatusCode;
    }

    public void setSuccessHttpStatusCode(int successHttpStatusCode) {
        this.successHttpStatusCode = successHttpStatusCode;
    }

    public int getErrorHttpStatusCode() {
        return errorHttpStatusCode;
    }

    public void setErrorHttpStatusCode(int errorHttpStatusCode) {
        this.errorHttpStatusCode = errorHttpStatusCode;
    }

    public String getTransactionIdMdcKey() {
        return transactionIdMdcKey == null ? correlationIdMdcKey : transactionIdMdcKey;
    }

    public void setTransactionIdMdcKey(String transactionIdMdcKey) {
        this.transactionIdMdcKey = transactionIdMdcKey;
    }

    public String getInternalTransactionIdMdcKey() {
        return internalTransactionIdMdcKey == null ? correlationIdMdcKey : internalTransactionIdMdcKey;
    }

    public void setInternalTransactionIdMdcKey(String internalTransactionIdMdcKey) {
        this.internalTransactionIdMdcKey = internalTransactionIdMdcKey;
    }
}
