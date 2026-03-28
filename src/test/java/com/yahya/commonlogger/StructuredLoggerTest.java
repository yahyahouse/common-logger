package com.yahya.commonlogger;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLoggerTest {

    @Test
    void onSuccessShouldLogCorrectPayload() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId("TestApi");
        StructuredLogger logger = new StructuredLogger(props);

        String logs = captureOutput(() -> {
            logger.newLog()
                    .withTransactionId("tx-123")
                    .withCorrelationId("corr-456")
                    .withRequest("my-request")
                    .withAdditionalData("customKey", "customValue")
                    .onSuccess("my-response", 150);
        });

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
        StructuredLogger logger = new StructuredLogger(props);

        String logs = captureOutput(() -> {
            logger.newLog()
                    .withApiId("ErrorApi")
                    .onFailure(new RuntimeException("Something went wrong"), 200);
        });

        assertThat(logs).contains("\"apiId\":\"ErrorApi\"");
        assertThat(logs).contains("\"logPoint\":\"Error\"");
        assertThat(logs).contains("\"error\":\"Something went wrong\"");
        assertThat(logs).contains("\"logException\":\"java.lang.RuntimeException: Something went wrong\"");
        assertThat(logs).contains("\"httpStatusCode\":500");
        assertThat(logs).contains("\"processTime\":200");
        assertThat(logs).contains("\"logLevel\":\"error\"");
    }

    @Test
    void onFailureShouldRespectCustomHttpStatusCode() throws Throwable {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setErrorHttpStatusCode(500); // default
        StructuredLogger logger = new StructuredLogger(props);

        String logs = captureOutput(() -> {
            logger.newLog()
                    .withHttpStatusCode(400)
                    .onFailure(new Exception("Wrong pin"), 100);
        });

        assertThat(logs).contains("\"httpStatusCode\":400");
        assertThat(logs).doesNotContain("\"httpStatusCode\":500");
    }

    private String captureOutput(Runnable runnable) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        System.setOut(ps);
        System.setErr(ps);
        try {
            runnable.run();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return baos.toString();
    }
}
