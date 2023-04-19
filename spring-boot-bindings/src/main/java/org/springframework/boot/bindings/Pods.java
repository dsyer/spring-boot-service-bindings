package org.springframework.boot.bindings;

import java.util.List;

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

	public List<V1Pod> forService(String namespace, String name) throws ApiException {
		V1Service service = api.readNamespacedService(name, namespace, null);
		V1LabelSelector v1LabelSelector = new V1LabelSelector().matchLabels(service.getSpec().getSelector());
		LabelSelector labelSelector = LabelSelector.parse(v1LabelSelector);
		return api.listNamespacedPod(namespace, null, null, null, null, labelSelector.toString(), null, null, null,
				null, null).getItems();
	}

}
