package com.gearshow.backend;

import com.gearshow.backend.support.TestInfraConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestInfraConfig.class)
class GearShowApplicationTests {

	@Test
	void contextLoads() {
	}
}
