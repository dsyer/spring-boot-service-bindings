package org.springframework.boot.bindings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.cloud.bindings.Binding;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.cloud.bindings.boot.AwkwardEnvironmentPostProcessor;
import org.springframework.cloud.bindings.boot.BindingsPropertiesProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

public class BindingsSpringApplicationRunListener implements SpringApplicationRunListener {

	private final SpringApplication application;

	public BindingsSpringApplicationRunListener(SpringApplication application, String[] args) {
		this.application = application;
	}

	@Override
	public void environmentPrepared(ConfigurableBootstrapContext context,
			ConfigurableEnvironment environment) {
		List<BindingsPropertiesProcessor> processors = SpringFactoriesLoader
				.loadFactories(BindingsPropertiesProcessor.class, getClass().getClassLoader());
		Bindings bindings = new Bindings(bindings(context, environment));
		new AwkwardEnvironmentPostProcessor(bindings, processors).postProcessEnvironment(environment, application);
	}

	private Binding[] bindings(ConfigurableBootstrapContext context, ConfigurableEnvironment environment) {
		SecretsBindings secrets = new SecretsBindings(context, environment);
		List<StrippedSourceContainer> sources = secrets.load();
		List<Binding> bindings = new ArrayList<>();
		if (sources != null) {
			for (StrippedSourceContainer source : sources) {
				Binding binding = binding(source);
				if (binding != null) {
					bindings.add(binding);
				}
			}
		}
		return bindings.toArray(new Binding[0]);
	}

	private Binding binding(StrippedSourceContainer source) {
		Map<String, String> map = new HashMap<>((Map<String, String>) source.data());
		if (!map.containsKey("type")) {
			return null;
		}
		return new Binding(source.name(), Path.of(source.name()), map);
	}

}