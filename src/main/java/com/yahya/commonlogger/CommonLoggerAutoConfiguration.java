package com.yahya.commonlogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AutoConfiguration
@ConditionalOnClass(Logger.class)
@EnableConfigurationProperties(CommonLoggerProperties.class)
public class CommonLoggerAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CommonLoggerAutoConfiguration.class);

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
    @ConditionalOnMissingBean
    public ObjectMapper commonLoggerObjectMapper() {
        return new ObjectMapper();
    }

    @Bean("defaultSensitiveDataMasker")
    @ConditionalOnMissingBean(name = "defaultSensitiveDataMasker")
    public SensitiveDataMasker defaultSensitiveDataMasker(CommonLoggerProperties properties) {
        return payload -> {
            Set<String> fields = new HashSet<>(properties.getSensitiveFields());
            if (!fields.isEmpty()) {
                maskRecursive(payload, fields);
            }
        };
    }

    @Bean
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnMissingBean
    public LoggingAspect loggingAspect(CommonLoggerProperties properties,
                                       ObjectProvider<List<StructuredLogCustomizer>> customizersProvider,
                                       ObjectProvider<List<SensitiveDataMasker>> maskersProvider,
                                       ObjectMapper commonLoggerObjectMapper) {
        List<StructuredLogCustomizer> customizers = customizersProvider.getIfAvailable(Collections::emptyList);
        List<SensitiveDataMasker> maskers = maskersProvider.getIfAvailable(Collections::emptyList);
        return new LoggingAspect(properties, customizers, maskers, commonLoggerObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public StructuredLogger structuredLogger(CommonLoggerProperties properties,
                                             ObjectMapper commonLoggerObjectMapper,
                                             ObjectProvider<List<SensitiveDataMasker>> maskersProvider) {
        List<SensitiveDataMasker> maskers = maskersProvider.getIfAvailable(Collections::emptyList);
        return new StructuredLogger(properties, commonLoggerObjectMapper, maskers);
    }

    @SuppressWarnings("unchecked")
    private static void maskRecursive(Map<String, Object> map, Set<String> sensitiveFields) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (sensitiveFields.contains(entry.getKey())) {
                entry.setValue("***");
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                maskRecursive((Map<String, Object>) nested, sensitiveFields);
            }
        }
    }
}
