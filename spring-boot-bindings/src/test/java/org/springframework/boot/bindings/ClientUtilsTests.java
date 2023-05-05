package org.springframework.boot.bindings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kubernetes.client.util.KubeConfig;

public class ClientUtilsTests {

	@Test
	public void test() {
		ClientUtils.config().getNamespace(); // might be null
	}

	@Test
	public void testDefaultConfig() {
		KubeConfig config = ClientUtils.defaultConfig();
		assertThat(config.getNamespace()).isEqualTo("default");
	}

	@Test
	public void testKubeConfig() {
		KubeConfig config = new KubeConfig(
				new ArrayList<>(Arrays.asList(Map.of("name", "bar", "context", Map.of("namespace", "foo")))),
				new ArrayList<>(), new ArrayList<>());
		config.setContext("bar");
		assertThat(config.getNamespace()).isEqualTo("foo");
	}

}
