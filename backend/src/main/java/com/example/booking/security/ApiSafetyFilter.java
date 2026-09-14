package com.example.booking.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;

@Component
@Order(-200)
public class ApiSafetyFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new HashMap<>();

    private final Semaphore requests = new Semaphore(64);

    @Value("${app.security.requests-per-minute:240}")
    private int requestLimit = 240;

    @Value("${app.security.max-request-bytes:65536}")
    private long maxBytes = 65536;

    public static class PayloadTooLarge extends RuntimeException {}

    private record Bucket(long expiresAt, int count) {}

    synchronized boolean allow(String key, long now) {
        buckets
            .entrySet()
            .removeIf(entry -> entry.getValue().expiresAt() <= now);

        Bucket previous = buckets.get(key);

        if (previous == null && buckets.size() >= 10000) return false;
        if (previous != null && previous.count() >= requestLimit) return false;

        buckets.put(
            key,
            new Bucket(
                previous == null ? now + 60000 : previous.expiresAt(),
                previous == null ? 1 : previous.count() + 1
            )
        );

        return true;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request
            .getRequestURI()
            .startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        String correlationId = UUID.randomUUID().toString();

        request.setAttribute("correlationId", correlationId);

        response.setHeader("X-Request-ID", correlationId);

        response.setHeader("Cache-Control", "no-store");

        if (request.getContentLengthLong() > maxBytes) {
            response.sendError(413, "Request body is too large");

            return;
        }
        // Do not trust client-supplied forwarding headers. A production gateway should
        // enforce distributed user/tenant limits in addition to this instance/IP budget.
        if (
            !allow(request.getRemoteAddr(), System.currentTimeMillis()) ||
            !requests.tryAcquire()
        ) {
            response.setHeader("Retry-After", "60");

            response.sendError(429, "Too many requests; try again shortly");

            return;
        }

        try {
            chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    private ServletInputStream bounded;

                    @Override
                    public ServletInputStream getInputStream()
                        throws IOException {
                        if (bounded != null) return bounded;
                        ServletInputStream input = super.getInputStream();

                        bounded = new ServletInputStream() {
                            private long count;

                            private void count(int bytes) {
                                if (
                                    bytes > 0 && (count += bytes) > maxBytes
                                ) throw new PayloadTooLarge();
                            }

                            public int read() throws IOException {
                                int value = input.read();

                                count(value == -1 ? 0 : 1);

                                return value;
                            }

                            public int read(byte[] data, int offset, int length)
                                throws IOException {
                                int bytes = input.read(data, offset, length);

                                count(bytes);

                                return bytes;
                            }

                            public boolean isFinished() {
                                return input.isFinished();
                            }

                            public boolean isReady() {
                                return input.isReady();
                            }

                            public void setReadListener(ReadListener listener) {
                                input.setReadListener(listener);
                            }
                        };

                        return bounded;
                    }

                    @Override
                    public BufferedReader getReader() throws IOException {
                        return new BufferedReader(
                            new InputStreamReader(
                                getInputStream(),
                                StandardCharsets.UTF_8
                            )
                        );
                    }
                },
                response
            );
        } finally {
            requests.release();
        }
    }
}
