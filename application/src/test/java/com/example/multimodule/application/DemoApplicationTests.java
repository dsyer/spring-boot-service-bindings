package com.example.multimodule.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;

@SpringBootTest(properties={"spring.cloud.kubernetes.client.namespace=default"})
public class DemoApplicationTests {

	@Autowired
	private ConfigurableEnvironment environment;

	@Test
	public void contextLoads() {
		assertThat(environment.getProperty("spring.datasource.url")).isNotNull();
	}

}
