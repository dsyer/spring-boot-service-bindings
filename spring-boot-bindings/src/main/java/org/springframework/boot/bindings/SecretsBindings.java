package org.springframework.boot.bindings;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.util.ObjectUtils;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Secret;

public class SecretsBindings {

	private final ConfigurableBootstrapContext context;
	private final String namespace;

	public SecretsBindings(ConfigurableBootstrapContext context, String namespace) {
		this.context = context;
		this.namespace = namespace;
	}

	public List<StrippedSourceContainer> load() {
		return strippedSecrets(context.get(CoreV1Api.class), namespace);
	}

	private static List<StrippedSourceContainer> strippedSecrets(CoreV1Api coreV1Api, String namespace) {
		List<StrippedSourceContainer> strippedSecrets = KubernetesClientSecretsCache.byNamespace(coreV1Api, namespace);
		return strippedSecrets;
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
		return secrets.stream().map(secret -> new StrippedSourceContainer(owner(secret), secret.getMetadata().getLabels(),
				secret.getMetadata().getName(), transform(secret.getData()))).collect(Collectors.toList());
	}

	private static V1OwnerReference owner(V1Secret secret) {
		if (secret.getMetadata().getOwnerReferences() != null && !secret.getMetadata().getOwnerReferences().isEmpty()) {
			return secret.getMetadata().getOwnerReferences().get(0);
		}
		return null;
	}

	private static Map<String, String> transform(Map<String, byte[]> in) {
		return ObjectUtils.isEmpty(in) ? Map.of()
				: in.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, en -> new String(en.getValue())));
	}

}