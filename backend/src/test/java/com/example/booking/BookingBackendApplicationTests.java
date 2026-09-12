package com.example.booking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = "app.security.jwt-enabled=false"
)
class BookingBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
