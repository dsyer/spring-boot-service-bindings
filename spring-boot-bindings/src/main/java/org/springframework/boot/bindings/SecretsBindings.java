package org.springframework.boot.bindings;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ObjectUtils;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
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

class KubernetesClientSecretsCache {

	/**
	 * at the moment our loading of config maps is using a single thread, but might
	 * change in the future, thus a thread safe structure.
	 */
	private static final ConcurrentHashMap<String, List<StrippedSourceContainer>> CACHE = new ConcurrentHashMap<>();

	static List<StrippedSourceContainer> byNamespace(CoreV1Api coreV1Api, String namespace) {
		List<StrippedSourceContainer> result = CACHE.computeIfAbsent(namespace, x -> {
			try {
				return strippedSecrets(coreV1Api
						.listNamespacedSecret(namespace, null, null, null, null, null, null, null, null, null, null)
						.getItems());
			} catch (ApiException apiException) {
				throw new RuntimeException(apiException.getResponseBody(), apiException);
			}
		});
		return result;
	}

	private static List<StrippedSourceContainer> strippedSecrets(List<V1Secret> secrets) {
		return secrets.stream().map(secret -> new StrippedSourceContainer(secret.getMetadata().getLabels(),
				secret.getMetadata().getName(), transform(secret.getData()))).collect(Collectors.toList());
	}

	private static Map<String, String> transform(Map<String, byte[]> in) {
		return ObjectUtils.isEmpty(in) ? Map.of()
				: in.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, en -> new String(en.getValue())));
	}

}