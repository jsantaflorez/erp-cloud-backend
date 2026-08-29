package com.erp.erp_cloud;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Boots the full Spring context against the real datasource in application.properties.
// Needs a live MySQL on localhost:3308 (see README/HELP) -- run via `./gradlew integrationTest`.
@Tag("integration")
@SpringBootTest
class ErpCloudApplicationTests {

	@Test
	void contextLoads() {
	}

}
