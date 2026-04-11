package com.yahya.commonlogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field or method parameter as sensitive so that its value is
 * redacted in structured log output.
 * <p>
 * When placed on a field in a request/response DTO, any {@link SensitiveDataMasker}
 * targeting that field name will replace the value with {@code "***"} in the log payload.
 * <p>
 * Example on a DTO field:
 * <pre>
 * {@code
 * public class LoginRequest {
 *     private String username;
 *
 *     @MaskField
 *     private String password;
 * }
 * }
 * </pre>
 *
 * Example on a method parameter in a {@link Loggable}-annotated method:
 * <pre>
 * {@code
 * @Loggable
 * public void processPayment(@MaskField String cardNumber, int amount) { ... }
 * }
 * </pre>
 *
 * @see SensitiveDataMasker
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaskField {

    /**
     * The replacement string used instead of the actual value.
     * Defaults to {@code "***"}.
     */
    String mask() default "***";
}
