package org.springframework.boot.bindings;

import java.util.Map;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;

public record StrippedSourceContainer(V1ObjectMeta meta, Map<String, String> data) {

	public V1OwnerReference owner() {
		return meta.getOwnerReferences() != null && meta.getOwnerReferences().isEmpty()
				? meta.getOwnerReferences().get(0) : null;
	}

	public String name() {
		return meta.getName();
	}

}
