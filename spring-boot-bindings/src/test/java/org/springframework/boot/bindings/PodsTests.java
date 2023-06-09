package org.springframework.boot.bindings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;

public class PodsTests {

	private static String namespace = ClientUtils.config().getNamespace() == null ? "default"
			: ClientUtils.config().getNamespace();

	@Test
	void testPods() throws Exception {
		Pods pods = pods();
		assertThat(pods.forService(namespace, "demo-db")).isNotEmpty();
	}

	private static Pods pods() {
		ApiClient client = ClientUtils.kubernetesApiClient();
		CoreV1Api api = new CoreV1Api(client);
		Pods pods = new Pods(api);
		return pods;
	}

	public static void main(String[] args) throws Exception {
		Pods pods = pods();
		RemoteService forward = pods.portForward(pods.forService("my-apps", "demo-db").get(0), 3306);
		System.err.println(forward);
		forward.close();
		forward = pods.portForward(pods.forService("my-apps", "demo-db").get(0), 3306);
		System.err.println(forward);
		forward.close();
	}

}
