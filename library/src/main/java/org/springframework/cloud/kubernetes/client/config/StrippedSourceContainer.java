package org.springframework.cloud.kubernetes.client.config;

import java.util.Map;

public record StrippedSourceContainer(Map<String, String> labels, String name, Map<String, String> data) {

}
