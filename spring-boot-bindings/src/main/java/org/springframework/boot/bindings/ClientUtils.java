package org.springframework.boot.bindings;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;

public class ClientUtils {

	private static final String ENV_HOME = "HOME";
	private static final String KUBEDIR = ".kube";
	private static final String KUBECONFIG = "config";
	private static final String ENV_KUBECONFIG = "KUBECONFIG";

	static ApiClient kubernetesApiClient() {
		try {
			// Assume we are running in a cluster
			ApiClient apiClient = ClientBuilder.cluster().build();
			return apiClient;
		} catch (Exception e) {
			try {
				ApiClient apiClient = ClientBuilder.defaultClient();
				return apiClient;
			} catch (Exception e1) {
				return new ClientBuilder().build();
			}
		}
	}

	static KubeConfig config() {
		try {
			File config = findConfigFromEnv();
			if (config == null) {
				config = findConfigInHomeDir();
			}
			if (config != null) {
				try (FileReader reader = new FileReader(config)) {
					KubeConfig kubeConfig = KubeConfig.loadKubeConfig(reader);
					return kubeConfig;
				}
			}
		} catch (Exception e) {
		}
		KubeConfig config = defaultConfig();

		return config;
	}

	static KubeConfig defaultConfig() {
		KubeConfig config = new KubeConfig(
				new ArrayList<>(Arrays.asList(Map.of("name", "context", "context",
						Map.of("namespace", "default")))),
				new ArrayList<>(),
				new ArrayList<>());
		config.setContext("context");
		return config;
	}

	private static File findConfigFromEnv() {
		final KubeConfigEnvParser kubeConfigEnvParser = new KubeConfigEnvParser();

		final String kubeConfigPath = kubeConfigEnvParser.parseKubeConfigPath(System.getenv(ENV_KUBECONFIG));
		if (kubeConfigPath == null) {
			return null;
		}
		final File kubeConfig = new File(kubeConfigPath);
		if (kubeConfig.exists()) {
			return kubeConfig;
		} else {
			return null;
		}
	}

	private static class KubeConfigEnvParser {
		private String parseKubeConfigPath(String kubeConfigEnv) {
			if (kubeConfigEnv == null) {
				return null;
			}

			final String[] filePaths = kubeConfigEnv.split(File.pathSeparator);
			final String kubeConfigPath = filePaths[0];

			return kubeConfigPath;
		}
	}

	private static File findHomeDir() {
		final String envHome = System.getenv(ENV_HOME);
		if (envHome != null && envHome.length() > 0) {
			final File config = new File(envHome);
			if (config.exists()) {
				return config;
			}
		}
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			String homeDrive = System.getenv("HOMEDRIVE");
			String homePath = System.getenv("HOMEPATH");
			if (homeDrive != null
					&& homeDrive.length() > 0
					&& homePath != null
					&& homePath.length() > 0) {
				File homeDir = new File(new File(homeDrive), homePath);
				if (homeDir.exists()) {
					return homeDir;
				}
			}
			String userProfile = System.getenv("USERPROFILE");
			if (userProfile != null && userProfile.length() > 0) {
				File profileDir = new File(userProfile);
				if (profileDir.exists()) {
					return profileDir;
				}
			}
		}
		return null;
	}

	private static File findConfigInHomeDir() {
		final File homeDir = findHomeDir();
		if (homeDir != null) {
			final File config = new File(new File(homeDir, KUBEDIR), KUBECONFIG);
			if (config.exists()) {
				return config;
			}
		}
		return null;
	}
}
