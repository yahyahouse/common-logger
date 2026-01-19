package com.yahya.commonlogger;

import org.aspectj.lang.ProceedingJoinPoint;

import java.util.Map;

/**
 * Hook for services to add or override structured log fields at runtime.
 */
@FunctionalInterface
public interface StructuredLogCustomizer {

    /**
     * Customize the log payload.
     *
     * @param payload  mutable map containing default fields
     * @param joinPoint current join point
     * @param result   method result (null if void or before return)
     * @param duration execution time in milliseconds
     * @param success  true if no exception thrown
     * @param failure  captured exception if present
     */
    void customize(Map<String, Object> payload,
                   ProceedingJoinPoint joinPoint,
                   Object result,
                   long duration,
                   boolean success,
                   Throwable failure);
}
