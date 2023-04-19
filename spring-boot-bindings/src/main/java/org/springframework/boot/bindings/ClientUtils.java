package org.springframework.boot.bindings;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;

public class ClientUtils {

	static ApiClient kubernetesApiClient() {
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
