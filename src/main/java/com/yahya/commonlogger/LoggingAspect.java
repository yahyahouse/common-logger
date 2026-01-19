package com.yahya.commonlogger;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LogLevel;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

@Aspect
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    private final CommonLoggerProperties properties;
    private final List<StructuredLogCustomizer> customizers;

    public LoggingAspect(CommonLoggerProperties properties, List<StructuredLogCustomizer> customizers) {
        this.properties = properties;
        this.customizers = customizers == null ? Collections.emptyList() : customizers;
    }

    @Around("@annotation(com.yahya.commonlogger.Loggable) || @within(com.yahya.commonlogger.Loggable)")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = false;
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - start;
            LogLevel configuredLevel = resolveConfiguredLevel();
            LogLevel levelToUse = failure != null ? LogLevel.ERROR : configuredLevel;
            boolean shouldLog = failure != null ? logger.isErrorEnabled() : isLevelEnabled(configuredLevel);

            if (shouldLog) {
                String payload = buildStructuredPayload(joinPoint, result, duration, success, failure, levelToUse);
                emitPlain(payload, failure != null);
            }
        }
    }

    private String buildStructuredPayload(ProceedingJoinPoint joinPoint,
                                          Object result,
                                          long duration,
                                          boolean success,
                                          Throwable failure,
                                          LogLevel logLevel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("logLevel", logLevel.name().toLowerCase(Locale.ROOT));
        payload.put("apiId", resolveApiId(joinPoint));
        payload.put("httpStatusCode", resolveStatusCode(failure));
        payload.put("internalTransactionId", resolveInternalTransactionId());
        payload.put("logMessage", buildLogMessage(joinPoint, success));
        payload.put("logPoint", buildLogPoint(joinPoint, success));
        payload.put("logTimestamp", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()));
        payload.put("processTime", duration);
        payload.put("transactionId", resolveTransactionId());

        if (failure != null) {
            payload.put("error", failure.getMessage());
            payload.put("logException", buildExceptionDetails(failure));
        }

        for (StructuredLogCustomizer customizer : customizers) {
            try {
                customizer.customize(payload, joinPoint, result, duration, success, failure);
            } catch (Exception ex) {
                logger.warn("StructuredLogCustomizer [{}] failed: {}", customizer.getClass().getName(), ex.getMessage());
            }
        }

        return toJson(payload);
    }

    private LogLevel resolveConfiguredLevel() {
        LogLevel level = properties.getLogLevel();
        return level == null ? LogLevel.INFO : level;
    }

    private boolean isLevelEnabled(LogLevel level) {
        return switch (level) {
            case TRACE -> logger.isTraceEnabled();
            case DEBUG -> logger.isDebugEnabled();
            case INFO -> logger.isInfoEnabled();
            case WARN -> logger.isWarnEnabled();
            case ERROR, FATAL -> logger.isErrorEnabled();
            case OFF -> false;
        };
    }

    private void emitPlain(String payload, boolean isError) {
        if (isError) {
            System.err.println(payload);
        } else {
            System.out.println(payload);
        }
    }

    private String resolveApiId(ProceedingJoinPoint joinPoint) {
        if (StringUtils.hasText(properties.getApiId())) {
            return properties.getApiId();
        }
        String declaringType = joinPoint.getSignature().getDeclaringTypeName();
        if (!StringUtils.hasText(declaringType)) {
            return "unknown";
        }
        int lastDot = declaringType.lastIndexOf('.');
        return lastDot >= 0 ? declaringType.substring(lastDot + 1) : declaringType;
    }

    private int resolveStatusCode(Throwable failure) {
        return failure == null ? properties.getSuccessHttpStatusCode() : properties.getErrorHttpStatusCode();
    }

    private String resolveInternalTransactionId() {
        String id = MDC.get(properties.getInternalTransactionIdMdcKey());
        if (StringUtils.hasText(id)) {
            return id;
        }
        return resolveTransactionId();
    }

    private String resolveTransactionId() {
        String id = MDC.get(properties.getTransactionIdMdcKey());
        if (StringUtils.hasText(id)) {
            return id;
        }
        return MDC.get(properties.getCorrelationIdMdcKey());
    }

    private String buildLogMessage(ProceedingJoinPoint joinPoint, boolean success) {
        String method = toMethodKey(joinPoint);
        String apiId = resolveApiId(joinPoint);
        return apiId + "-" + method + (success ? " Completed" : " Failed");
    }

    private String buildLogPoint(ProceedingJoinPoint joinPoint, boolean success) {
        String method = toMethodKey(joinPoint);
        String apiId = resolveApiId(joinPoint);
        return apiId + "-" + method + "-" + (success ? "End" : "Error");
    }

    private String toMethodKey(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature().getName().toLowerCase(Locale.ROOT);
    }

    private String toJson(Map<String, Object> payload) {
        StringBuilder builder = new StringBuilder("{ ");
        Iterator<Map.Entry<String, Object>> iterator = payload.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            builder.append("\"")
                    .append(escape(entry.getKey()))
                    .append("\": ")
                    .append(formatValue(entry.getValue()));
            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append(" }");
        return builder.toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String buildExceptionDetails(Throwable failure) {
        StringWriter sw = new StringWriter();
        failure.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
