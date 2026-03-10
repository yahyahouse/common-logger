package com.yahya.commonlogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A service for manually creating structured JSON logs.
 * Provides a thread-safe, fluent builder API for constructing and writing logs.
 */
@Component
public class StructuredLogger {

    private static final Logger log = LoggerFactory.getLogger(StructuredLogger.class);
    private final CommonLoggerProperties properties;
    private final ObjectMapper objectMapper;

    public StructuredLogger(CommonLoggerProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a new, thread-safe log builder instance.
     * @return A new instance of StructuredLogBuilder.
     */
    public StructuredLogBuilder newLog() {
        return new StructuredLogBuilder();
    }

    /**
     * A fluent builder for creating a structured log message.
     * Each instance is intended for a single log event.
     */
    public class StructuredLogBuilder {
        private final Map<String, Object> payload = new HashMap<>();
        private LogLevel successLevel;
        private LogLevel errorLevel = LogLevel.ERROR;
        private boolean httpStatusCodeSet = false;

        private StructuredLogBuilder() {
            // Initialize with default values from properties
            this.successLevel = properties.getLogLevel();
            this.payload.put("apiId", properties.getApiId());
            this.payload.put("httpStatusCode", properties.getSuccessHttpStatusCode());
        }

        public StructuredLogBuilder withTransactionId(String transactionId) {
            this.payload.put("transactionId", transactionId);
            return this;
        }

        public StructuredLogBuilder withCorrelationId(String correlationId) {
            this.payload.put("correlationId", correlationId);
            return this;
        }

        public StructuredLogBuilder withApiId(String apiId) {
            this.payload.put("apiId", apiId);
            return this;
        }

        public StructuredLogBuilder withHttpStatusCode(int statusCode) {
            this.payload.put("httpStatusCode", statusCode);
            this.httpStatusCodeSet = true;
            return this;
        }

        public StructuredLogBuilder withLogLevel(LogLevel level) {
            this.successLevel = level;
            return this;
        }

        public StructuredLogBuilder withErrorLogLevel(LogLevel level) {
            this.errorLevel = level;
            return this;
        }

        public StructuredLogBuilder withRequest(Object request) {
            this.payload.put("request", request);
            return this;
        }
        
        public StructuredLogBuilder withAdditionalData(String key, Object value) {
            this.payload.put(key, value);
            return this;
        }
        
        private void log(LogLevel level, Map<String, Object> finalPayload) {
            finalPayload.put("logLevel", level.name().toLowerCase());
            finalPayload.put("logTimestamp", Instant.now().toString());
            
            String logMessage = "Structured log";
            if (finalPayload.containsKey("apiId")) {
                 logMessage = String.format("%s completed", finalPayload.get("apiId"));
            }
            finalPayload.putIfAbsent("logMessage", logMessage);

            try {
                StringWriter sw = new StringWriter();
                objectMapper.writeValue(sw, finalPayload);
                String jsonLog = sw.toString();
                
                switch (level) {
                    case TRACE: log.trace(jsonLog); break;
                    case DEBUG: log.debug(jsonLog); break;
                    case WARN:  log.warn(jsonLog);  break;
                    case ERROR: log.error(jsonLog); break;
                    default:    log.info(jsonLog);  break;
                }
            } catch (Exception e) {
                log.error("Failed to serialize structured log payload", e);
            }
        }

        /**
         * Finalizes and logs a success event.
         * @param response The response object to include in the log (can be null).
         * @param processTimeMillis The processing time in milliseconds.
         */
        public void onSuccess(Object response, long processTimeMillis) {
            this.payload.put("logPoint", "End");
            this.payload.put("response", response);
            this.payload.put("processTime", processTimeMillis);
            log(this.successLevel, this.payload);
        }

        /**
         * Finalizes and logs a failure event.
         * @param throwable The exception or error that caused the failure.
         * @param processTimeMillis The processing time in milliseconds until the failure.
         */
        public void onFailure(Throwable throwable, long processTimeMillis) {
            this.payload.put("logPoint", "Error");
            this.payload.put("logException", throwable.toString());
            this.payload.put("error", throwable.getMessage());
            if (!this.httpStatusCodeSet) {
                this.payload.put("httpStatusCode", properties.getErrorHttpStatusCode());
            }
            this.payload.put("processTime", processTimeMillis);
            log(this.errorLevel, this.payload);
        }
    }
}
