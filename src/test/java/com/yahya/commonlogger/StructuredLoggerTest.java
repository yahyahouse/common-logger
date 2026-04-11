package com.yahya.commonlogger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLoggerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ch.qos.logback.classic.Logger structuredLoggerLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        structuredLoggerLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StructuredLogger.class);
        appender = new ListAppender<>();
        appender.start();
        structuredLoggerLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        structuredLoggerLogger.detachAppender(appender);
    }

    private String capturedLogs() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void onSuccessShouldLogCorrectPayload() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("TestApi");
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of());

        logger.newLog()
                .withTransactionId("tx-123")
                .withCorrelationId("corr-456")
                .withRequest("my-request")
                .withAdditionalData("customKey", "customValue")
                .onSuccess("my-response", 150);

        String logs = capturedLogs();
        assertThat(logs).contains("\"apiId\":\"TestApi\"");
        assertThat(logs).contains("\"transactionId\":\"tx-123\"");
        assertThat(logs).contains("\"correlationId\":\"corr-456\"");
        assertThat(logs).contains("\"request\":\"my-request\"");
        assertThat(logs).contains("\"response\":\"my-response\"");
        assertThat(logs).contains("\"customKey\":\"customValue\"");
        assertThat(logs).contains("\"processTime\":150");
        assertThat(logs).contains("\"logPoint\":\"End\"");
        assertThat(logs).contains("\"logLevel\":\"info\"");
    }

    @Test
    void onFailureShouldLogCorrectPayload() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of());

        logger.newLog()
                .withApiId("ErrorApi")
                .onFailure(new RuntimeException("Something went wrong"), 200);

        String logs = capturedLogs();
        assertThat(logs).contains("\"apiId\":\"ErrorApi\"");
        assertThat(logs).contains("\"logPoint\":\"Error\"");
        assertThat(logs).contains("\"error\":\"Something went wrong\"");
        assertThat(logs).contains("\"logException\":\"java.lang.RuntimeException: Something went wrong");
        assertThat(logs).contains("at com.yahya.commonlogger.StructuredLoggerTest");
        assertThat(logs).contains("\"httpStatusCode\":500");
        assertThat(logs).contains("\"processTime\":200");
        assertThat(logs).contains("\"logLevel\":\"error\"");
    }

    @Test
    void onFailureShouldAddServerErrorType() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setErrorHttpStatusCode(500);
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of());

        logger.newLog()
                .onFailure(new RuntimeException("internal error"), 100);

        assertThat(capturedLogs()).contains("\"errorType\":\"SERVER_ERROR\"");
    }

    @Test
    void onFailureShouldAddClientErrorTypeWhenStatusIs4xx() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of());

        logger.newLog()
                .withHttpStatusCode(404)
                .onFailure(new RuntimeException("not found"), 50);

        assertThat(capturedLogs()).contains("\"errorType\":\"CLIENT_ERROR\"");
    }

    @Test
    void doesNotLogWhenLevelDisabled() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of());

        ch.qos.logback.classic.Logger slf4jLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(StructuredLogger.class);
        ch.qos.logback.classic.Level original = slf4jLogger.getLevel();
        slf4jLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
        try {
            logger.newLog()
                    .withApiId("SilentApi")
                    .onSuccess("response", 10);
        } finally {
            slf4jLogger.setLevel(original);
        }

        assertThat(appender.list).isEmpty();
    }

    @Test
    void onFailureShouldRespectCustomHttpStatusCode() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setErrorHttpStatusCode(500);
        StructuredLogger logger = new StructuredLogger(props, OBJECT_MAPPER, List.of());

        logger.newLog()
                .withHttpStatusCode(400)
                .onFailure(new Exception("Wrong pin"), 100);

        String logs = capturedLogs();
        assertThat(logs).contains("\"httpStatusCode\":400");
        assertThat(logs).doesNotContain("\"httpStatusCode\":500");
    }
}
