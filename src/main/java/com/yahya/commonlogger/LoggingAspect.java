package com.yahya.commonlogger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LogLevel;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AOP aspect that intercepts methods annotated with {@link Loggable} (on method or class level)
 * and emits a structured JSON log payload for each invocation.
 *
 * <p>The payload includes: {@code logLevel}, {@code apiId}, {@code httpStatusCode},
 * {@code logMessage}, {@code logPoint}, {@code logTimestamp}, {@code processTime},
 * {@code transactionId}. On failure, {@code errorType}, {@code error}, and
 * {@code logException} (full stack trace) are added.
 *
 * <p>The payload can be extended via {@link StructuredLogCustomizer} beans registered
 * in the Spring context. Sensitive fields can be redacted via {@link SensitiveDataMasker} beans.
 *
 * <p>Registered automatically by {@link CommonLoggerAutoConfiguration} when AspectJ is on
 * the classpath.
 */
@Aspect
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    private final CommonLoggerProperties properties;
    private final List<StructuredLogCustomizer> customizers;
    private final List<SensitiveDataMasker> maskers;
    private final ObjectMapper objectMapper;

    public LoggingAspect(CommonLoggerProperties properties,
                         List<StructuredLogCustomizer> customizers,
                         List<SensitiveDataMasker> maskers,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.customizers = customizers == null ? Collections.emptyList() : customizers;
        this.maskers = maskers == null ? Collections.emptyList() : maskers;
        this.objectMapper = objectMapper;
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
                emit(payload, levelToUse);
            }
        }
    }

    private String buildStructuredPayload(ProceedingJoinPoint joinPoint,
                                          Object result,
                                          long duration,
                                          boolean success,
                                          Throwable failure,
                                          LogLevel logLevel) {
        int statusCode = resolveStatusCode(failure);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("logLevel", logLevel.name().toLowerCase(Locale.ROOT));
        payload.put("apiId", resolveApiId(joinPoint));
        payload.put("httpStatusCode", statusCode);
        payload.put("logMessage", buildLogMessage(joinPoint, success));
        payload.put("logPoint", buildLogPoint(joinPoint, success));
        payload.put("logTimestamp", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()));
        payload.put("processTime", duration);
        payload.put("transactionId", resolveTransactionId());

        if (failure != null) {
            payload.put("errorType", resolveErrorType(statusCode));
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

        for (SensitiveDataMasker masker : maskers) {
            try {
                masker.mask(payload);
            } catch (Exception ex) {
                logger.warn("SensitiveDataMasker [{}] failed: {}", masker.getClass().getName(), ex.getMessage());
            }
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            logger.warn("Failed to serialize log payload: {}", ex.getMessage());
            return "{\"logLevel\":\"" + logLevel.name().toLowerCase(Locale.ROOT) + "\",\"error\":\"log serialization failed\"}";
        }
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

    private void emit(String payload, LogLevel level) {
        switch (level) {
            case TRACE -> logger.trace(payload);
            case DEBUG -> logger.debug(payload);
            case WARN -> logger.warn(payload);
            case ERROR, FATAL -> logger.error(payload);
            default -> logger.info(payload);
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

    private String resolveErrorType(int statusCode) {
        if (statusCode >= 400 && statusCode < 500) {
            return "CLIENT_ERROR";
        } else if (statusCode >= 500 && statusCode < 600) {
            return "SERVER_ERROR";
        }
        return "UNKNOWN_ERROR";
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

    private String buildExceptionDetails(Throwable failure) {
        return ExceptionUtils.getStackTrace(failure);
    }
}
