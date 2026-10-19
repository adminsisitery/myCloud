package com.cloudrive.pan_common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
    private static final String TRACE_ATTRIBUTE = RequestTraceFilter.class.getName() + ".traceId";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() { return false; }

    @Override
    protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        String traceId = (String) request.getAttribute(TRACE_ATTRIBUTE);
        if (traceId == null) {
            traceId = TraceContext.newTraceId();
            request.setAttribute(TRACE_ATTRIBUTE, traceId);
        }
        response.setHeader("X-Trace-Id", traceId);
        try (TraceContext ignored = TraceContext.open(Map.of("traceId", traceId))) {
            chain.doFilter(request, response);
        }
    }
}
