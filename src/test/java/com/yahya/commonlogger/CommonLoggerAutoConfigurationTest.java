package com.yahya.commonlogger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CommonLoggerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonLoggerAutoConfiguration.class));

    @Test
    void registersBeansAutomatically() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CommonLoggerProperties.class);
            assertThat(context).hasSingleBean(StructuredLogger.class);
            assertThat(context).hasSingleBean(LoggingAspect.class);
            assertThat(context).hasSingleBean(CorrelationIdFilter.class);
        });
    }

    @Test
    void respectsCustomProperties() {
        contextRunner.withPropertyValues("common.logger.api-id=TestApi")
                .run(context -> {
                    CommonLoggerProperties props = context.getBean(CommonLoggerProperties.class);
                    assertThat(props.getApiId()).isEqualTo("TestApi");
                });
    }
}
