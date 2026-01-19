package com.yahya.commonlogger;

import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass(Logger.class)
public class CommonLoggerAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CommonLoggerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CommonLoggerProperties commonLoggerProperties() {
        return new CommonLoggerProperties();
    }

    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter(CommonLoggerProperties properties) {
        if (logger.isDebugEnabled()) {
            logger.debug("Registering CorrelationIdFilter with header [{}]", properties.getCorrelationIdHeader());
        }
        return new CorrelationIdFilter(properties);
    }

    @Bean
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnMissingBean
    public LoggingAspect loggingAspect(CommonLoggerProperties properties,
                                       ObjectProvider<List<StructuredLogCustomizer>> customizersProvider) {
        List<StructuredLogCustomizer> customizers = customizersProvider.getIfAvailable(Collections::emptyList);
        return new LoggingAspect(properties, customizers);
    }
}
