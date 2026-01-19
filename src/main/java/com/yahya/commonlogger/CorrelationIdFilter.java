package com.yahya.commonlogger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures every request has a correlation identifier and exposes it via MDC and response headers.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    private final CommonLoggerProperties properties;

    public CorrelationIdFilter(CommonLoggerProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String headerName = properties.getCorrelationIdHeader();
        String correlationId = request.getHeader(headerName);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        String mdcKey = properties.getCorrelationIdMdcKey();
        MDC.put(mdcKey, correlationId);
        try {
            response.setHeader(headerName, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(mdcKey);
        }
    }
}
