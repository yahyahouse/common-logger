package com.yahya.commonlogger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensitiveDataMaskingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ch.qos.logback.classic.Logger aspectLogger;
    private ch.qos.logback.classic.Logger structuredLogger;
    private ListAppender<ILoggingEvent> aspectAppender;
    private ListAppender<ILoggingEvent> structuredAppender;

    @BeforeEach
    void setUp() {
        aspectLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LoggingAspect.class);
        aspectAppender = new ListAppender<>();
        aspectAppender.start();
        aspectLogger.addAppender(aspectAppender);

        structuredLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StructuredLogger.class);
        structuredAppender = new ListAppender<>();
        structuredAppender.start();
        structuredLogger.addAppender(structuredAppender);
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(aspectAppender);
        structuredLogger.detachAppender(structuredAppender);
    }

    private String capturedAspectLogs() {
        return aspectAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    private String capturedStructuredLogs() {
        return structuredAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    // ── LoggingAspect tests ──────────────────────────────────────────────────

    @Test
    void aspect_masksFieldsAddedByCustomizer() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        StructuredLogCustomizer customizer = (payload, jp, result, duration, success, failure) -> {
            payload.put("password", "secret123");
            payload.put("token", "bearer-abc");
            payload.put("username", "john");
        };
        SensitiveDataMasker masker = payload -> {
            if (payload.containsKey("password")) payload.put("password", "***");
            if (payload.containsKey("token")) payload.put("token", "***");
        };
        LoggingAspect aspect = new LoggingAspect(props, List.of(customizer), List.of(masker), OBJECT_MAPPER);

        aspect.logAround(mockJoinPoint("process", "com.example.Demo", "ok"));

        String logs = capturedAspectLogs();
        assertThat(logs).contains("\"password\":\"***\"");
        assertThat(logs).contains("\"token\":\"***\"");
        assertThat(logs).contains("\"username\":\"john\"");
    }

    @Test
    void aspect_masksPropertyBasedSensitiveFieldsViaDefaultMasker() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setSensitiveFields(List.of("cardNumber", "cvv"));

        SensitiveDataMasker defaultMasker = buildPropertyMasker(props);
        StructuredLogCustomizer customizer = (payload, jp, result, duration, success, failure) -> {
            payload.put("cardNumber", "4111-1111-1111-1111");
            payload.put("cvv", "123");
            payload.put("amount", 100);
        };
        LoggingAspect aspect = new LoggingAspect(props, List.of(customizer), List.of(defaultMasker), OBJECT_MAPPER);

        aspect.logAround(mockJoinPoint("pay", "com.example.PaymentService", "ok"));

        String logs = capturedAspectLogs();
        assertThat(logs).contains("\"cardNumber\":\"***\"");
        assertThat(logs).contains("\"cvv\":\"***\"");
        assertThat(logs).contains("\"amount\":100");
    }

    @Test
    void aspect_appliesMultipleMaskers() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        StructuredLogCustomizer customizer = (payload, jp, result, duration, success, failure) -> {
            payload.put("password", "secret");
            payload.put("ssn", "123-45-6789");
            payload.put("name", "Alice");
        };
        SensitiveDataMasker masker1 = payload -> payload.put("password", "***");
        SensitiveDataMasker masker2 = payload -> payload.put("ssn", "***");
        LoggingAspect aspect = new LoggingAspect(props, List.of(customizer), List.of(masker1, masker2), OBJECT_MAPPER);

        aspect.logAround(mockJoinPoint("register", "com.example.UserService", "ok"));

        String logs = capturedAspectLogs();
        assertThat(logs).contains("\"password\":\"***\"");
        assertThat(logs).contains("\"ssn\":\"***\"");
        assertThat(logs).contains("\"name\":\"Alice\"");
    }

    @Test
    void aspect_maskerThrowingExceptionDoesNotBreakLogging() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("ResilientApi");
        SensitiveDataMasker faultyMasker = payload -> { throw new RuntimeException("masker exploded"); };
        LoggingAspect aspect = new LoggingAspect(props, List.of(), List.of(faultyMasker), OBJECT_MAPPER);

        aspect.logAround(mockJoinPoint("run", "com.example.Demo", "ok"));

        assertThat(capturedAspectLogs()).contains("\"apiId\":\"ResilientApi\"");
    }

    // ── StructuredLogger tests ───────────────────────────────────────────────

    @Test
    void structuredLogger_masksRequestFields() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        SensitiveDataMasker masker = payload -> {
            if (payload.get("request") instanceof Map<?, ?> request) {
                @SuppressWarnings("unchecked")
                Map<String, Object> req = (Map<String, Object>) request;
                if (req.containsKey("password")) req.put("password", "***");
            }
        };
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of(masker));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("username", "alice");
        request.put("password", "supersecret");

        logger.newLog()
                .withApiId("LoginApi")
                .withRequest(request)
                .onSuccess("token-xyz", 50);

        String logs = capturedStructuredLogs();
        assertThat(logs).contains("\"username\":\"alice\"");
        assertThat(logs).contains("\"password\":\"***\"");
        assertThat(logs).doesNotContain("supersecret");
    }

    @Test
    void structuredLogger_masksAdditionalDataFields() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setSensitiveFields(List.of("token"));
        SensitiveDataMasker masker = buildPropertyMasker(props);
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of(masker));

        logger.newLog()
                .withApiId("TokenApi")
                .withAdditionalData("token", "bearer-secret")
                .withAdditionalData("userId", "u-42")
                .onSuccess(null, 30);

        String logs = capturedStructuredLogs();
        assertThat(logs).contains("\"token\":\"***\"");
        assertThat(logs).contains("\"userId\":\"u-42\"");
        assertThat(logs).doesNotContain("bearer-secret");
    }

    @Test
    void structuredLogger_masksFieldsOnFailure() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        SensitiveDataMasker masker = payload -> payload.put("password", "***");
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of(masker));

        logger.newLog()
                .withAdditionalData("password", "should-be-masked")
                .onFailure(new RuntimeException("auth failed"), 10);

        String logs = capturedStructuredLogs();
        assertThat(logs).contains("\"password\":\"***\"");
        assertThat(logs).doesNotContain("should-be-masked");
    }

    @Test
    void structuredLogger_maskerThrowingExceptionDoesNotBreakLogging() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("SafeApi");
        SensitiveDataMasker faultyMasker = payload -> { throw new RuntimeException("masker exploded"); };
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of(faultyMasker));

        logger.newLog()
                .withApiId("SafeApi")
                .onSuccess("result", 20);

        assertThat(capturedStructuredLogs()).contains("\"apiId\":\"SafeApi\"");
    }

    // ── Recursive masking via property-based masker ──────────────────────────

    @Test
    void propertyMasker_masksNestedMapFields() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setSensitiveFields(List.of("password"));
        SensitiveDataMasker masker = buildPropertyMasker(props);
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of(masker));

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("username", "bob");
        nested.put("password", "nested-secret");

        logger.newLog()
                .withAdditionalData("credentials", nested)
                .onSuccess(null, 10);

        String logs = capturedStructuredLogs();
        assertThat(logs).contains("\"username\":\"bob\"");
        assertThat(logs).contains("\"password\":\"***\"");
        assertThat(logs).doesNotContain("nested-secret");
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private SensitiveDataMasker buildPropertyMasker(CommonLoggerProperties props) {
        return payload -> {
            java.util.Set<String> fields = new java.util.HashSet<>(props.getSensitiveFields());
            if (!fields.isEmpty()) {
                maskRecursive(payload, fields);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static void maskRecursive(Map<String, Object> map, java.util.Set<String> sensitiveFields) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (sensitiveFields.contains(entry.getKey())) {
                entry.setValue("***");
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                maskRecursive((Map<String, Object>) nested, sensitiveFields);
            }
        }
    }

    private ProceedingJoinPoint mockJoinPoint(String method, String className, Object returnValue) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(method);
        when(signature.getDeclaringTypeName()).thenReturn(className);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[0]);
        when(pjp.proceed()).thenReturn(returnValue);
        return pjp;
    }
}
