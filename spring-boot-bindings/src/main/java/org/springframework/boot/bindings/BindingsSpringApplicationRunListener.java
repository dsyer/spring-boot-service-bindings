package org.springframework.boot.bindings;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.StringUtils;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.KubeConfig;

public class BindingsSpringApplicationRunListener implements SpringApplicationRunListener {

	private final SpringApplication application;

	public BindingsSpringApplicationRunListener(SpringApplication application, String[] args) {
		this.application = application;
	}

	@Override
	public void environmentPrepared(ConfigurableBootstrapContext context, ConfigurableEnvironment environment) {
		if (!Binder.get(environment).bind("spring.service-bindings.enabled", Boolean.class).orElse(false)) {
			return;
		}
		List<BindingsPropertiesProcessor> processors = SpringFactoriesLoader
				.loadFactories(BindingsPropertiesProcessor.class, getClass().getClassLoader());
		context.registerIfAbsent(ApiClient.class, InstanceSupplier.from(ClientUtils::kubernetesApiClient));
		context.registerIfAbsent(CoreV1Api.class,
				InstanceSupplier.from(() -> new CoreV1Api(context.get(ApiClient.class))));
		Binding[] bindings = bindings(context, environment);
		if (bindings.length == 0) {
			return;
		}
		SpringApplication.getShutdownHandlers()
				.add(() -> context.get(ApiClient.class).getHttpClient().dispatcher().executorService().shutdown());
		new AwkwardEnvironmentPostProcessor(new Bindings(bindings), processors).postProcessEnvironment(environment,
				application);
	}

	private static String namespace(Environment environment) {
		KubeConfig config = ClientUtils.config();
		String namespace = config == null ? "default"
				: (config.getNamespace() == null ? "default" : config.getNamespace());
		return Binder.get(environment).bind("spring.cloud.kubernetes.client.namespace", String.class)
				.orElse(Binder.get(environment).bind("pod.namespace", String.class).orElse(namespace));
	}

	private Binding[] bindings(ConfigurableBootstrapContext context, ConfigurableEnvironment environment) {
		String namespace = namespace(environment);
		SecretsBindings secrets = new SecretsBindings(context, namespace);
		List<StrippedSourceContainer> sources = secrets.load();
		List<StrippedSourceContainer> bindings = new ArrayList<>();
		Map<V1OwnerReference, List<StrippedSourceContainer>> dict = new HashMap<>();
		if (sources != null) {
			for (StrippedSourceContainer source : sources) {
				Binding binding = binding(source);
				if (binding != null) {
					if (source.owner() != null) {
						dict.computeIfAbsent(source.owner(), owner -> new ArrayList<>()).add(source);
					}
					else {
						bindings.add(source);
					}
				}
			}
		}
		for (V1OwnerReference owner : dict.keySet()) {
			List<StrippedSourceContainer> list = dict.get(owner);
			StrippedSourceContainer binding = locate(list);
			bindings.add(binding);
		}
		List<Binding> result = new ArrayList<>();
		for (StrippedSourceContainer source : bindings) {
			Binding binding = forward(context, namespace, source);
			result.add(binding);
		}
		return result.toArray(new Binding[0]);
	}

	private StrippedSourceContainer locate(List<StrippedSourceContainer> list) {
		if (list.size() == 1) {
			return list.get(0);
		}
		// reverse order of version
		Collections.sort(list, (a, b) -> b.meta().getResourceVersion().compareTo(a.meta().getResourceVersion()));
		return list.get(0);
	}

	private Binding forward(ConfigurableBootstrapContext context, String namespace, StrippedSourceContainer source) {
		Binding binding = binding(source);
		Map<String, String> secret = new HashMap<>(source.data());
		if (secret.containsKey("host") && secret.containsKey("port")) {
			String host = secret.get("host");
			// Small hack for bitnami service claims where the owner is the namespace
			if (host.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) {
				if (source.owner() != null) {
					String owner = source.owner().getName();
					namespace = owner;
					host = owner;
				}
			}
			// Smaller hack for service claims where the namespace is included in the host
			if (host.matches(".*\\.svc.*") || StringUtils.countOccurrencesOf(host, ".") == 1) {
				try {
					InetAddress.getByName(host);
					// If it's resolvable it must be OK not to forward
					return binding;
				}
				catch (UnknownHostException e) {
					String[] tokens = host.split("\\.");
					if (tokens.length > 1) {
						namespace = tokens[1];
						host = tokens[0];
					}
				}
			}
			int port = Integer.parseInt(secret.get("port"));
			Pods pods = new Pods(context.get(CoreV1Api.class));
			try {
				V1Pod pod = pods.forService(namespace, host).get(0);
				if (pod == null) {
					// can't find pod
					return binding;
				}
				RemoteService remote = pods.portForward(pod, port);
				SpringApplication.getShutdownHandlers().add(() -> remote.close());
				secret.put("host", "localhost");
				secret.put("port", "" + remote.getLocalPort());
				secret.put("type", binding.getType());
				if (binding.getProvider() != null) {
					secret.put("provider", binding.getProvider());
				}
				binding = new Binding(binding.getName(), binding.getPath(), secret);
			}
			catch (Exception e) {
			}
		}
		return binding;
	}

	private Binding binding(StrippedSourceContainer source) {
		Map<String, String> map = new HashMap<>((Map<String, String>) source.data());
		if (!map.containsKey("type")) {
			return null;
		}
		if (source.owner() != null) {
			map.put("owner", source.owner().getName());
		}
		return new Binding(source.name(), Path.of(source.name()), map);
	}

}
