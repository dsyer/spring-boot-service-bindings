package com.example.multimodule.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;

@SpringBootTest
public class DemoApplicationTests {

	@Autowired
	private ConfigurableEnvironment environment;

	@Test
	public void contextLoads() {
		System.err.println(environment.getProperty("spring.datasource.url"));
		assertThat(environment.getProperty("spring.datasource.url")).isNotNull();
	}

}
