package com.yahya.commonlogger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    private CommonLoggerProperties properties;
    private CorrelationIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        properties = new CommonLoggerProperties();
        filter = new CorrelationIdFilter(properties);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @Test
    void generatesCorrelationIdIfMissing() throws ServletException, IOException {
        filter.doFilter(request, response, filterChain);

        String correlationId = response.getHeader(properties.getCorrelationIdHeader());
        assertThat(correlationId).isNotBlank();
        assertThat(MDC.get(properties.getCorrelationIdMdcKey())).isNull(); // MDC is cleared in finally block
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void usesExistingCorrelationIdFromHeader() throws ServletException, IOException {
        String existingId = "existing-corr-id";
        request.addHeader(properties.getCorrelationIdHeader(), existingId);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(properties.getCorrelationIdHeader())).isEqualTo(existingId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void clearsMdcAfterRequest() throws ServletException, IOException {
        filter.doFilter(request, response, filterChain);
        assertThat(MDC.get(properties.getCorrelationIdMdcKey())).isNull();
    }

    @Test
    void respectsCustomHeaderAndMdcKey() throws ServletException, IOException {
        properties.setCorrelationIdHeader("X-Custom-ID");
        properties.setCorrelationIdMdcKey("customKey");

        String existingId = "custom-id-123";
        request.addHeader("X-Custom-ID", existingId);

        // We need to verify MDC.put was called with "customKey"
        // But MDC is cleared in finally. 
        // We can check MDC inside the filter chain if we mock it carefully.
        
        filterChain = (req, res) -> {
            assertThat(MDC.get("customKey")).isEqualTo(existingId);
        };

        filter.doFilter(request, response, filterChain);
        
        assertThat(response.getHeader("X-Custom-ID")).isEqualTo(existingId);
    }
}
