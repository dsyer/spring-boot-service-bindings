package org.springframework.boot.bindings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;

public class PodsTests {

	@Test
	void testPods() throws Exception {
		ApiClient client = ClientUtils.kubernetesApiClient();
		CoreV1Api api = new CoreV1Api(client);
		assertThat(new Pods(api).forService("default", "demo-db")).isNotEmpty();
	}
}
