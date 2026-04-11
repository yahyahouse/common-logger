package com.yahya.commonlogger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LogLevel;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingAspectTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ch.qos.logback.classic.Logger aspectLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        aspectLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LoggingAspect.class);
        appender = new ListAppender<>();
        appender.start();
        aspectLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(appender);
        MDC.clear();
    }

    private String capturedLogs() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    private LoggingAspect aspect(CommonLoggerProperties props, List<StructuredLogCustomizer> customizers) {
        return new LoggingAspect(props, customizers, List.of(), OBJECT_MAPPER);
    }

    private LoggingAspect aspect(CommonLoggerProperties props,
                                 List<StructuredLogCustomizer> customizers,
                                 List<SensitiveDataMasker> maskers) {
        return new LoggingAspect(props, customizers, maskers, OBJECT_MAPPER);
    }

    @Test
    void logsStructuredPayloadWithDefaultsAndAdditionalFields() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("SendNotification");
        props.setSuccessHttpStatusCode(200);
        props.setTransactionIdMdcKey("transactionId");

        StructuredLogCustomizer customizer = (payload, jp, result, duration, success, failure) -> {
            payload.put("tenantId", "t-1");
            payload.put("region", "apac");
        };
        LoggingAspect aspect = aspect(props, List.of(customizer));

        ProceedingJoinPoint pjp = mockJoinPoint("doWork", "com.example.Demo", new Object[]{"arg1"}, "ok");
        MDC.put(props.getCorrelationIdMdcKey(), "corr-123");
        MDC.put("transactionId", "tx-123");

        Object result = aspect.logAround(pjp);
        assertThat(result).isEqualTo("ok");

        String logs = capturedLogs();
        assertThat(logs).contains("\"logLevel\":\"info\"");
        assertThat(logs).contains("\"apiId\":\"SendNotification\"");
        assertThat(logs).contains("\"httpStatusCode\":200");
        assertThat(logs).contains("\"logMessage\":\"SendNotification-dowork Completed\"");
        assertThat(logs).contains("\"logPoint\":\"SendNotification-dowork-End\"");
        assertThat(logs).contains("\"transactionId\":\"tx-123\"");
        assertThat(logs).contains("\"processTime\":");
        assertThat(logs).contains("\"logTimestamp\":");
        assertThat(logs).contains("\"region\":\"apac\"");
        assertThat(logs).contains("\"tenantId\":\"t-1\"");
    }

    @Test
    void logsErrorPayloadOnException() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        LoggingAspect aspect = aspect(props, List.of());

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("fails");
        when(signature.getDeclaringTypeName()).thenReturn("com.example.Demo");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[0]);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.logAround(pjp)).isInstanceOf(IllegalStateException.class);

        String logs = capturedLogs();
        assertThat(logs).contains("\"logMessage\":\"Demo-fails Failed\"");
        assertThat(logs).contains("\"logPoint\":\"Demo-fails-Error\"");
        assertThat(logs).contains("\"httpStatusCode\":500");
        assertThat(logs).contains("\"error\":\"boom\"");
        assertThat(logs).contains("\"logException\":\"java.lang.IllegalStateException: boom");
        assertThat(logs).contains("\"logLevel\":\"error\"");
    }

    @Test
    void logsAtConfiguredDebugLevelWhenEnabled() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("DebugApi");
        props.setLogLevel(LogLevel.DEBUG);
        LoggingAspect aspect = aspect(props, List.of());

        Level original = aspectLogger.getLevel();
        aspectLogger.setLevel(Level.DEBUG);
        ProceedingJoinPoint pjp = mockJoinPoint("debuggable", "com.example.Demo", new Object[0], "ok");
        try {
            aspect.logAround(pjp);
        } finally {
            aspectLogger.setLevel(original);
        }

        String logs = capturedLogs();
        assertThat(logs).contains("\"logLevel\":\"debug\"");
        assertThat(logs).contains("\"logPoint\":\"DebugApi-debuggable-End\"");
    }

    @Test
    void fallsBackToClassNameWhenApiIdMissing() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        LoggingAspect aspect = aspect(props, List.of());

        ProceedingJoinPoint pjp = mockJoinPoint("process", "com.example.InventoryService", new Object[0], "done");
        aspect.logAround(pjp);

        String logs = capturedLogs();
        assertThat(logs).contains("\"apiId\":\"InventoryService\"");
        assertThat(logs).contains("\"logMessage\":\"InventoryService-process Completed\"");
        assertThat(logs).contains("\"logPoint\":\"InventoryService-process-End\"");
    }

    @Test
    void usesCustomStatusCodesAndTransactionFallback() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setSuccessHttpStatusCode(201);
        LoggingAspect aspect = aspect(props, List.of());

        ProceedingJoinPoint pjp = mockJoinPoint("fetch", "com.example.Api", new Object[0], "ok");
        MDC.put(props.getCorrelationIdMdcKey(), "corr-xyz");
        aspect.logAround(pjp);

        String logs = capturedLogs();
        assertThat(logs).contains("\"httpStatusCode\":201");
        assertThat(logs).contains("\"transactionId\":\"corr-xyz\"");
    }

    @Test
    void usesCustomErrorStatusCodeAndTransactionId() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setErrorHttpStatusCode(503);
        props.setTransactionIdMdcKey("txid");
        LoggingAspect aspect = aspect(props, List.of());

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("fails");
        when(signature.getDeclaringTypeName()).thenReturn("com.example.Demo");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[0]);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        MDC.put("txid", "tx-1");
        assertThatThrownBy(() -> aspect.logAround(pjp)).isInstanceOf(IllegalStateException.class);

        String logs = capturedLogs();
        assertThat(logs).contains("\"httpStatusCode\":503");
        assertThat(logs).contains("\"transactionId\":\"tx-1\"");
        assertThat(logs).contains("\"logException\":\"java.lang.IllegalStateException: boom");
        assertThat(logs).contains("\"logLevel\":\"error\"");
    }

    @Test
    void logsWithOnlyTransactionIdSet() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("TransactionOnlyApi");
        props.setTransactionIdMdcKey("customTxKey");
        LoggingAspect aspect = aspect(props, List.of());

        ProceedingJoinPoint pjp = mockJoinPoint("process", "com.example.Service", new Object[0], "ok");
        MDC.put("customTxKey", "just-a-transaction-id");
        aspect.logAround(pjp);

        String logs = capturedLogs();
        assertThat(logs).contains("\"transactionId\":\"just-a-transaction-id\"");
        assertThat(logs).contains("\"apiId\":\"TransactionOnlyApi\"");
        assertThat(logs).contains("\"logLevel\":\"info\"");
        assertThat(logs).contains("\"httpStatusCode\":200");
        assertThat(logs).contains("\"logMessage\":\"TransactionOnlyApi-process Completed\"");
        assertThat(logs).doesNotContain("\"correlationId\":");
    }

    @Test
    void logsStructuredPayloadWithMultipleCustomizers() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("MultiCustomizerApi");

        StructuredLogCustomizer customizer1 = (payload, jp, result, duration, success, failure) ->
                payload.put("field1", "value1");
        StructuredLogCustomizer customizer2 = (payload, jp, result, duration, success, failure) ->
                payload.put("field2", "value2");
        LoggingAspect aspect = aspect(props, List.of(customizer1, customizer2));

        ProceedingJoinPoint pjp = mockJoinPoint("doWork", "com.example.Demo", new Object[0], "ok");
        aspect.logAround(pjp);

        String logs = capturedLogs();
        assertThat(logs).contains("\"field1\":\"value1\"");
        assertThat(logs).contains("\"field2\":\"value2\"");
        assertThat(logs).contains("\"apiId\":\"MultiCustomizerApi\"");
    }

    @Test
    void escapesSpecialCharactersInCustomizerFields() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        LoggingAspect aspect = aspect(props, List.of(
                (payload, jp, result, duration, success, failure) -> {
                    payload.put("withQuote", "say \"hello\"");
                    payload.put("withBackslash", "C:\\Users\\file");
                    payload.put("withNewline", "line1\nline2");
                    payload.put("withTab", "col1\tcol2");
                    payload.put("withBackspace", "a\bb");
                    payload.put("withFormFeed", "a\fb");
                    payload.put("withControlChar", "a\u0001b");
                }
        ));

        ProceedingJoinPoint pjp = mockJoinPoint("run", "com.example.Demo", new Object[0], "ok");
        aspect.logAround(pjp);

        String logs = capturedLogs();
        assertThat(logs).contains("\"withQuote\":\"say \\\"hello\\\"\"");
        assertThat(logs).contains("\"withBackslash\":\"C:\\\\Users\\\\file\"");
        assertThat(logs).contains("\"withNewline\":\"line1\\nline2\"");
        assertThat(logs).contains("\"withTab\":\"col1\\tcol2\"");
        assertThat(logs).contains("\"withBackspace\":\"a\\bb\"");
        assertThat(logs).contains("\"withFormFeed\":\"a\\fb\"");
        assertThat(logs).contains("\"withControlChar\":\"a\\u0001b\"");
    }

    @Test
    void classifiesServerErrorOnException() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setErrorHttpStatusCode(500);
        LoggingAspect aspect = aspect(props, List.of());

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("run");
        when(signature.getDeclaringTypeName()).thenReturn("com.example.Demo");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[0]);
        when(pjp.proceed()).thenThrow(new RuntimeException("internal error"));

        assertThatThrownBy(() -> aspect.logAround(pjp)).isInstanceOf(RuntimeException.class);

        assertThat(capturedLogs()).contains("\"errorType\":\"SERVER_ERROR\"");
    }

    @Test
    void classifiesClientErrorWhenStatusIs4xx() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setErrorHttpStatusCode(400);
        LoggingAspect aspect = aspect(props, List.of());

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("run");
        when(signature.getDeclaringTypeName()).thenReturn("com.example.Demo");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[0]);
        when(pjp.proceed()).thenThrow(new IllegalArgumentException("bad input"));

        assertThatThrownBy(() -> aspect.logAround(pjp)).isInstanceOf(IllegalArgumentException.class);

        assertThat(capturedLogs()).contains("\"errorType\":\"CLIENT_ERROR\"");
    }

    @Test
    void customizerThrowingExceptionDoesNotBreakLogging() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("ResilientApi");
        StructuredLogCustomizer faultyCustomizer = (payload, jp, result, duration, success, failure) -> {
            throw new RuntimeException("customizer exploded");
        };
        StructuredLogCustomizer goodCustomizer = (payload, jp, result, duration, success, failure) -> {
            payload.put("afterFault", "still-here");
        };
        LoggingAspect aspect = aspect(props, List.of(faultyCustomizer, goodCustomizer));

        ProceedingJoinPoint pjp = mockJoinPoint("run", "com.example.Demo", new Object[0], "ok");
        aspect.logAround(pjp);

        String logs = capturedLogs();
        assertThat(logs).contains("\"apiId\":\"ResilientApi\"");
        assertThat(logs).contains("\"afterFault\":\"still-here\"");
    }

    @Test
    void doesNotBuildPayloadWhenLogLevelDisabled() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setLogLevel(LogLevel.DEBUG);
        LoggingAspect aspect = aspect(props, List.of());

        Level original = aspectLogger.getLevel();
        aspectLogger.setLevel(Level.ERROR);
        ProceedingJoinPoint pjp = mockJoinPoint("run", "com.example.Demo", new Object[0], "ok");
        try {
            aspect.logAround(pjp);
        } finally {
            aspectLogger.setLevel(original);
        }

        assertThat(appender.list).isEmpty();
    }

    private ProceedingJoinPoint mockJoinPoint(String method,
                                              String className,
                                              Object[] args,
                                              Object returnValue) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(method);
        when(signature.getDeclaringTypeName()).thenReturn(className);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed()).thenReturn(returnValue);
        return pjp;
    }
}
