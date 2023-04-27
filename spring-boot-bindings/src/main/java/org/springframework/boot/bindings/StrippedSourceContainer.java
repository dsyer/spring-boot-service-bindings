package org.springframework.boot.bindings;

import java.util.Map;

import io.kubernetes.client.openapi.models.V1OwnerReference;

public record StrippedSourceContainer(V1OwnerReference owner, Map<String, String> labels, String name, Map<String, String> data) {

}
