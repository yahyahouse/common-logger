package com.yahya.commonlogger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonLoggerPropertiesTest {

    @Test
    void defaultValuesAreCorrect() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        assertThat(props.getCorrelationIdHeader()).isEqualTo("X-Correlation-Id");
        assertThat(props.getCorrelationIdMdcKey()).isEqualTo("correlationId");
        assertThat(props.getLogLevel()).isEqualTo(LogLevel.INFO);
        assertThat(props.getApiId()).isEqualTo("");
        assertThat(props.getSuccessHttpStatusCode()).isEqualTo(200);
        assertThat(props.getErrorHttpStatusCode()).isEqualTo(500);
        assertThat(props.getTransactionIdMdcKey()).isEqualTo("correlationId");
    }

    @Test
    void customValuesAreRespected() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setCorrelationIdHeader("X-ID");
        props.setCorrelationIdMdcKey("id");
        props.setLogLevel(LogLevel.DEBUG);
        props.setApiId("my-api");
        props.setSuccessHttpStatusCode(201);
        props.setErrorHttpStatusCode(503);
        props.setTransactionIdMdcKey("tx-id");

        assertThat(props.getCorrelationIdHeader()).isEqualTo("X-ID");
        assertThat(props.getCorrelationIdMdcKey()).isEqualTo("id");
        assertThat(props.getLogLevel()).isEqualTo(LogLevel.DEBUG);
        assertThat(props.getApiId()).isEqualTo("my-api");
        assertThat(props.getSuccessHttpStatusCode()).isEqualTo(201);
        assertThat(props.getErrorHttpStatusCode()).isEqualTo(503);
        assertThat(props.getTransactionIdMdcKey()).isEqualTo("tx-id");
    }

    @Test
    void logLevelNullSetsToInfo() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setLogLevel(null);
        assertThat(props.getLogLevel()).isEqualTo(LogLevel.INFO);
    }

    @Test
    void apiIdNullSetsToEmptyString() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setApiId(null);
        assertThat(props.getApiId()).isEqualTo("");
    }

    @Test
    void rejectsBlankCorrelationIdHeader() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        assertThatThrownBy(() -> props.setCorrelationIdHeader(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlation-id-header");
        assertThatThrownBy(() -> props.setCorrelationIdHeader(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankCorrelationIdMdcKey() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        assertThatThrownBy(() -> props.setCorrelationIdMdcKey("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlation-id-mdc-key");
        assertThatThrownBy(() -> props.setCorrelationIdMdcKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidSuccessHttpStatusCode() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        assertThatThrownBy(() -> props.setSuccessHttpStatusCode(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("success-http-status-code");
        assertThatThrownBy(() -> props.setSuccessHttpStatusCode(600))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidErrorHttpStatusCode() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        assertThatThrownBy(() -> props.setErrorHttpStatusCode(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error-http-status-code");
        assertThatThrownBy(() -> props.setErrorHttpStatusCode(600))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transactionIdMdcKeyDefaultsToCorrelationIdMdcKey() {
        CommonLoggerProperties props = new CommonLoggerProperties();
        props.setCorrelationIdMdcKey("custom-corr-key");
        assertThat(props.getTransactionIdMdcKey()).isEqualTo("custom-corr-key");
    }
}
