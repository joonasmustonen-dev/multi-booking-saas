package com.example.booking.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class RequestSafetyTest {

    @Test
    void rateLimitsExpireWithoutUnboundedKeyGrowth() {
        ApiSafetyFilter filter = new ApiSafetyFilter();

        ReflectionTestUtils.setField(filter, "requestLimit", 2);

        assertTrue(filter.allow("user-a", 1000));

        assertTrue(filter.allow("user-a", 1001));

        assertFalse(filter.allow("user-a", 1002));

        assertTrue(filter.allow("user-b", 1002));

        assertTrue(filter.allow("user-a", 61000));
    }

    @Test
    void rejectsOversizedKnownLengthAndChunkedBodiesAndAddsPrivacyHeaders()
        throws Exception {
        ApiSafetyFilter filter = new ApiSafetyFilter();

        ReflectionTestUtils.setField(filter, "maxBytes", 8L);

        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            "/api/v1/customers"
        );

        request.setContent(new byte[9]);

        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertEquals(413, response.getStatus());

        assertFalse(called.get());

        assertEquals("no-store", response.getHeader("Cache-Control"));

        assertNotNull(response.getHeader("X-Request-ID"));

        MockHttpServletRequest chunked = new MockHttpServletRequest(
            "POST",
            "/api/v1/customers"
        ) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };

        chunked.setContent(new byte[9]);

        assertThrows(ApiSafetyFilter.PayloadTooLarge.class, () ->
            filter.doFilter(
                chunked,
                new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes()
            )
        );
    }
}
