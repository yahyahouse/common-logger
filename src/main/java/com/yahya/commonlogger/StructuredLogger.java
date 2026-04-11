package com.yahya.commonlogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;

import java.io.StringWriter;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A service for manually creating structured JSON logs.
 * Provides a thread-safe, fluent builder API for constructing and writing logs.
 */
public class StructuredLogger {

    private static final Logger log = LoggerFactory.getLogger(StructuredLogger.class);
    private final CommonLoggerProperties properties;
    private final ObjectMapper objectMapper;
    private final List<SensitiveDataMasker> maskers;

    public StructuredLogger(CommonLoggerProperties properties,
                            ObjectMapper objectMapper,
                            List<SensitiveDataMasker> maskers) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.maskers = maskers == null ? Collections.emptyList() : maskers;
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
            if (!isLevelEnabled(level)) {
                return;
            }

            finalPayload.put("logLevel", level.name().toLowerCase());
            finalPayload.put("logTimestamp", Instant.now().toString());

            for (SensitiveDataMasker masker : maskers) {
                try {
                    masker.mask(finalPayload);
                } catch (Exception e) {
                    log.warn("SensitiveDataMasker [{}] failed: {}", masker.getClass().getName(), e.getMessage());
                }
            }
            
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
                    case TRACE -> log.trace(jsonLog);
                    case DEBUG -> log.debug(jsonLog);
                    case WARN -> log.warn(jsonLog);
                    case ERROR, FATAL -> log.error(jsonLog);
                    default -> log.info(jsonLog);
                }
            } catch (Exception e) {
                // Fallback to basic logging if JSON serialization fails
                log.error("Failed to serialize structured log payload", e);
            }
        }

        private String buildExceptionDetails(Throwable throwable) {
            return ExceptionUtils.getStackTrace(throwable);
        }

        private boolean isLevelEnabled(LogLevel level) {
            return switch (level) {
                case TRACE -> log.isTraceEnabled();
                case DEBUG -> log.isDebugEnabled();
                case INFO -> log.isInfoEnabled();
                case WARN -> log.isWarnEnabled();
                case ERROR, FATAL -> log.isErrorEnabled();
                case OFF -> false;
            };
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
            this.payload.put("logException", buildExceptionDetails(throwable));
            this.payload.put("error", throwable.getMessage());
            if (!this.httpStatusCodeSet) {
                this.payload.put("httpStatusCode", properties.getErrorHttpStatusCode());
            }
            int statusCode = (int) this.payload.get("httpStatusCode");
            this.payload.put("errorType", resolveErrorType(statusCode));
            this.payload.put("processTime", processTimeMillis);
            log(this.errorLevel, this.payload);
        }

        private String resolveErrorType(int statusCode) {
            if (statusCode >= 400 && statusCode < 500) {
                return "CLIENT_ERROR";
            } else if (statusCode >= 500 && statusCode < 600) {
                return "SERVER_ERROR";
            }
            return "UNKNOWN_ERROR";
        }
    }
}
