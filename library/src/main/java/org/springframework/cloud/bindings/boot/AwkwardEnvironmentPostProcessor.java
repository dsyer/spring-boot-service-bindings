package org.springframework.cloud.bindings.boot;

import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.core.env.ConfigurableEnvironment;

public class AwkwardEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private final Bindings bindings;
	private final List<BindingsPropertiesProcessor> processors;

	public AwkwardEnvironmentPostProcessor(Bindings bindings, List<BindingsPropertiesProcessor> processors) {
		this.bindings = bindings;
		this.processors = processors;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		PropertySourceContributor.contributePropertySource("global",
				Map.of("org.springframework.cloud.bindings.boot.enable", "true"), environment);
		new BindingSpecificEnvironmentPostProcessor(bindings, processors.toArray(new BindingsPropertiesProcessor[0]))
				.postProcessEnvironment(environment, application);
		environment.getPropertySources().remove("global");
	}

}
