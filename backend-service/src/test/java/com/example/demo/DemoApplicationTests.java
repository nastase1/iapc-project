package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.ai.openai.api-key=test-key",
		"spring.ai.openai-sdk.api-key=test-key",
		"spring.main.lazy-initialization=true"
})
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
