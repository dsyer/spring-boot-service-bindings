package org.springframework.boot.bindings;

import java.io.IOException;
import java.util.List;

import io.kubernetes.client.PortForward;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.labels.LabelSelector;

public class Pods {

	private final CoreV1Api api;

	public Pods(CoreV1Api api) {
		this.api = api;
	}

	public V1Pod byName(String namespace, String name) throws ApiException {
		List<V1Pod> items = api.listNamespacedPod(namespace, null, null, null, null, null, null, null, null, null, null)
				.getItems();
		return items.isEmpty() ? null : items.get(0);
	}

	public List<V1Pod> forService(String namespace, String name) throws ApiException {
		V1Service service = api.readNamespacedService(name, namespace, null);
		V1LabelSelector v1LabelSelector = new V1LabelSelector().matchLabels(service.getSpec().getSelector());
		LabelSelector labelSelector = LabelSelector.parse(v1LabelSelector);
		return api.listNamespacedPod(namespace, null, null, null, null, labelSelector.toString(), null, null, null,
				null, null).getItems();
	}

	@SuppressWarnings("resource")
	public RemoteService portForward(V1Pod pod, int targetPort) throws ApiException, IOException {

		ApiClient client = api.getApiClient();
		PortForward forward = new PortForward(client);
		return new RemoteService(forward, pod, targetPort).start();

	}

}
