package com.yahya.commonlogger;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.logging.LogLevel;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingAspectTest {

    @Test
    void logsStructuredPayloadWithDefaultsAndAdditionalFields() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("SendNotification");
        props.setSuccessHttpStatusCode(200);
        props.setTransactionIdMdcKey("transactionId");
        props.setInternalTransactionIdMdcKey("internalTxId");

        StructuredLogCustomizer customizer = (payload, jp, result, duration, success, failure) -> {
            payload.put("tenantId", "t-1");
            payload.put("region", "apac");
        };
        LoggingAspect aspect = new LoggingAspect(props, List.of(customizer));

        ProceedingJoinPoint pjp = mockJoinPoint("doWork", "com.example.Demo", new Object[]{"arg1"}, "ok");
        MDC.put(props.getCorrelationIdMdcKey(), "corr-123");
        MDC.put("transactionId", "tx-123");
        MDC.put("internalTxId", "internal-456");
        String logs;
        try {
            logs = captureOutput(() -> {
                Object result = aspect.logAround(pjp);
                assertThat(result).isEqualTo("ok");
            });
        } finally {
            MDC.clear();
        }

        assertThat(logs).contains("\"logLevel\": \"info\"");
        assertThat(logs).contains("\"apiId\": \"SendNotification\"");
        assertThat(logs).contains("\"httpStatusCode\": 200");
        assertThat(logs).contains("\"internalTransactionId\": \"internal-456\"");
        assertThat(logs).contains("\"logMessage\": \"SendNotification-dowork Completed\"");
        assertThat(logs).contains("\"logPoint\": \"SendNotification-dowork-End\"");
        assertThat(logs).contains("\"transactionId\": \"tx-123\"");
        assertThat(logs).contains("\"processTime\":");
        assertThat(logs).contains("\"logTimestamp\":");
        assertThat(logs).contains("\"region\": \"apac\"");
        assertThat(logs).contains("\"tenantId\": \"t-1\"");
    }

    @Test
    void logsErrorPayloadOnException() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        LoggingAspect aspect = new LoggingAspect(props, List.of());

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("fails");
        when(signature.getDeclaringTypeName()).thenReturn("com.example.Demo");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[0]);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        String logs = captureOutput(() -> assertThatThrownBy(() -> aspect.logAround(pjp)).isInstanceOf(IllegalStateException.class));

        assertThat(logs).contains("\"logMessage\": \"Demo-fails Failed\"");
        assertThat(logs).contains("\"logPoint\": \"Demo-fails-Error\"");
        assertThat(logs).contains("\"httpStatusCode\": 500");
        assertThat(logs).contains("\"error\": \"boom\"");
        assertThat(logs).contains("\"logException\": \"java.lang.IllegalStateException: boom");
        assertThat(logs).contains("\"logLevel\": \"error\"");
    }

    @Test
    void logsAtConfiguredDebugLevelWhenEnabled() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("DebugApi");
        props.setLogLevel(LogLevel.DEBUG);
        LoggingAspect aspect = new LoggingAspect(props, List.of());

        ch.qos.logback.classic.Logger aspectLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoggingAspect.class);
        ch.qos.logback.classic.Level original = aspectLogger.getLevel();
        aspectLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        ProceedingJoinPoint pjp = mockJoinPoint("debuggable", "com.example.Demo", new Object[0], "ok");
        String logs;
        try {
            logs = captureOutput(() -> aspect.logAround(pjp));
        } finally {
            aspectLogger.setLevel(original);
        }

        assertThat(logs).contains("\"logLevel\": \"debug\"");
        assertThat(logs).contains("\"logPoint\": \"DebugApi-debuggable-End\"");
    }

    @Test
    void fallsBackToClassNameWhenApiIdMissing() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        LoggingAspect aspect = new LoggingAspect(props, List.of());

        ProceedingJoinPoint pjp = mockJoinPoint("process", "com.example.InventoryService", new Object[0], "done");
        String logs = captureOutput(() -> aspect.logAround(pjp));

        assertThat(logs).contains("\"apiId\": \"InventoryService\"");
        assertThat(logs).contains("\"logMessage\": \"InventoryService-process Completed\"");
        assertThat(logs).contains("\"logPoint\": \"InventoryService-process-End\"");
    }

    @Test
    void usesCustomStatusCodesAndTransactionFallback() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setSuccessHttpStatusCode(201);
        LoggingAspect aspect = new LoggingAspect(props, List.of());

        ProceedingJoinPoint pjp = mockJoinPoint("fetch", "com.example.Api", new Object[0], "ok");
        MDC.put(props.getCorrelationIdMdcKey(), "corr-xyz");
        String logs;
        try {
            logs = captureOutput(() -> aspect.logAround(pjp));
        } finally {
            MDC.clear();
        }

        assertThat(logs).contains("\"httpStatusCode\": 201");
        assertThat(logs).contains("\"transactionId\": \"corr-xyz\"");
        assertThat(logs).contains("\"internalTransactionId\": \"corr-xyz\"");
    }

    @Test
    void usesCustomErrorStatusCodeAndInternalTransactionId() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setErrorHttpStatusCode(503);
        props.setTransactionIdMdcKey("txid");
        props.setInternalTransactionIdMdcKey("intid");
        LoggingAspect aspect = new LoggingAspect(props, List.of());

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("fails");
        when(signature.getDeclaringTypeName()).thenReturn("com.example.Demo");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[0]);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        MDC.put("txid", "tx-1");
        MDC.put("intid", "int-1");
        String logs;
        try {
            logs = captureOutput(() -> assertThatThrownBy(() -> aspect.logAround(pjp)).isInstanceOf(IllegalStateException.class));
        } finally {
            MDC.clear();
        }

        assertThat(logs).contains("\"httpStatusCode\": 503");
        assertThat(logs).contains("\"transactionId\": \"tx-1\"");
        assertThat(logs).contains("\"internalTransactionId\": \"int-1\"");
        assertThat(logs).contains("\"logException\": \"java.lang.IllegalStateException: boom");
        assertThat(logs).contains("\"logLevel\": \"error\"");
    }

    private String captureOutput(ThrowingCallable callable) throws Throwable {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        System.setOut(ps);
        System.setErr(ps);
        try {
            callable.call();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return baos.toString();
    }

    @FunctionalInterface
    interface ThrowingCallable {
        void call() throws Throwable;
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
