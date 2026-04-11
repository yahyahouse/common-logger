package com.yahya.commonlogger;

import java.util.Map;

/**
 * Extension hook for masking sensitive fields in the structured log payload.
 * <p>
 * Register as a Spring bean to apply custom masking before the payload is serialized.
 * Multiple implementations are supported and applied in registration order.
 * <p>
 * Example:
 * <pre>
 * {@code
 * @Bean
 * public SensitiveDataMasker myMasker() {
 *     return payload -> {
 *         if (payload.containsKey("ssn")) {
 *             payload.put("ssn", "***");
 *         }
 *     };
 * }
 * }
 * </pre>
 *
 * For property-based masking, use {@code common.logger.sensitive-fields}:
 * <pre>
 * common.logger.sensitive-fields=password,token,cardNumber
 * </pre>
 *
 * @see MaskField
 */
@FunctionalInterface
public interface SensitiveDataMasker {

    /**
     * Masks sensitive fields in the given log payload map.
     * Implementations may mutate the map in place (e.g., replacing values with {@code "***"}).
     *
     * @param payload the mutable log payload map
     */
    void mask(Map<String, Object> payload);
}
