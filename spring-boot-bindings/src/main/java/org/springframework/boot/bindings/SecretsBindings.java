package org.springframework.boot.bindings;

import java.util.List;

import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;

public class SecretsBindings {

	private final ConfigurableBootstrapContext context;
	private final ConfigurableEnvironment environment;

	public SecretsBindings(ConfigurableBootstrapContext context, ConfigurableEnvironment environment) {
		this.context = context;
		this.environment = environment;
	}

	public List<StrippedSourceContainer> load() {
		context.registerIfAbsent(ApiClient.class, InstanceSupplier.from(SecretsBindings::kubernetesApiClient));
		context.registerIfAbsent(CoreV1Api.class, InstanceSupplier.from(() -> new CoreV1Api(context.get(ApiClient.class))));
		String namespace = Binder.get(environment)
				.bind("spring.cloud.kubernetes.client.namespace", String.class).orElse("default");
		return strippedSecrets(context.get(CoreV1Api.class), namespace);
	}

	private static List<StrippedSourceContainer> strippedSecrets(CoreV1Api coreV1Api, String namespace) {
		List<StrippedSourceContainer> strippedSecrets = KubernetesClientSecretsCache.byNamespace(coreV1Api, namespace);
		return strippedSecrets;
	}

	private static ApiClient kubernetesApiClient() {
		try {
			// Assume we are running in a cluster
			ApiClient apiClient = ClientBuilder.cluster().build();
			return apiClient;
		}
		catch (Exception e) {
			try {
				ApiClient apiClient = ClientBuilder.defaultClient();
				return apiClient;
			}
			catch (Exception e1) {
				return new ClientBuilder().build();
			}
		}
	}

}
