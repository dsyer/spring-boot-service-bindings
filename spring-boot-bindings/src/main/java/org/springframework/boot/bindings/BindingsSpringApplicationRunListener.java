package org.springframework.boot.bindings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.bindings.Binding;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.cloud.bindings.boot.AwkwardEnvironmentPostProcessor;
import org.springframework.cloud.bindings.boot.BindingsPropertiesProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;

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
		context.registerIfAbsent(ApiClient.class, InstanceSupplier.from(ClientUtils::kubernetesApiClient));
		context.registerIfAbsent(CoreV1Api.class,
				InstanceSupplier.from(() -> new CoreV1Api(context.get(ApiClient.class))));
		SpringApplication.getShutdownHandlers()
				.add(() -> context.get(ApiClient.class).getHttpClient().dispatcher().executorService().shutdown());
		Bindings bindings = new Bindings(bindings(context, environment));
		new AwkwardEnvironmentPostProcessor(bindings, processors).postProcessEnvironment(environment, application);
	}

	private Binding[] bindings(ConfigurableBootstrapContext context, ConfigurableEnvironment environment) {
		String namespace = Binder.get(environment)
				.bind("spring.cloud.kubernetes.client.namespace", String.class).orElse("default");
		SecretsBindings secrets = new SecretsBindings(context, namespace);
		List<StrippedSourceContainer> sources = secrets.load();
		List<Binding> bindings = new ArrayList<>();
		if (sources != null) {
			for (StrippedSourceContainer source : sources) {
				Binding binding = binding(source);
				if (binding != null) {
					binding = forward(context, namespace, binding);
					bindings.add(binding);
				}
			}
		}
		return bindings.toArray(new Binding[0]);
	}

	private Binding forward(ConfigurableBootstrapContext context, String namespace, Binding binding) {
		Map<String, String> secret = new HashMap<>(binding.getSecret());
		if (secret.containsKey("host") && secret.containsKey("port")) {
			String host = secret.get("host");
			int port = Integer.parseInt(secret.get("port"));
			Pods pods = new Pods(context.get(CoreV1Api.class));
			try {
				V1Pod pod = pods.forService(namespace, host).get(0);
				RemoteService remote = pods.portForward(pod, port);
				SpringApplication.getShutdownHandlers().add(() -> remote.close());
				secret.put("host", "localhost");
				secret.put("port", "" + remote.getLocalPort());
				secret.put("type", binding.getType());
				if (binding.getProvider()!=null) {
					secret.put("provider", binding.getProvider());
				}
				binding = new Binding(binding.getName(), binding.getPath(), secret);
			} catch (Exception e) {
			}
		}
		return binding;
	}

	private Binding binding(StrippedSourceContainer source) {
		Map<String, String> map = new HashMap<>((Map<String, String>) source.data());
		if (!map.containsKey("type")) {
			return null;
		}
		return new Binding(source.name(), Path.of(source.name()), map);
	}

}
