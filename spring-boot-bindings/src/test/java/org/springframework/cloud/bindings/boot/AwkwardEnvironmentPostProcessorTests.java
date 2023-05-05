package org.springframework.cloud.bindings.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.bindings.Binding;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

public class AwkwardEnvironmentPostProcessorTests {

	@Test
	void testPostProcessEnvironment() {
		ConfigurableEnvironment environment = new StandardEnvironment();
		SpringApplication application = new SpringApplication();
		List<BindingsPropertiesProcessor> processors = SpringFactoriesLoader
				.loadFactories(BindingsPropertiesProcessor.class, getClass().getClassLoader());
		Bindings bindings = new Bindings(new Binding("test", Path.of("test"), Map.of("type", "mysql", "username",
				"root", "password", "root", "host", "localhost", "port", "3306", "database", "mysql")));
		new AwkwardEnvironmentPostProcessor(bindings, processors).postProcessEnvironment(environment, application);
		// System.err.println(environment.getProperty("spring.datasource.url"));
		assertThat(environment.getProperty("spring.datasource.url")).isNotNull();
		assertThat(environment.getPropertySources().get("global")).isNull();
	}

}
