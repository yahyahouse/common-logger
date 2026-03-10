package com.yahya.commonlogger;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLoggerTest {

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
