package org.springframework.boot.bindings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ClientUtilsTests {

	@Test
	public void test() {
		assertThat(ClientUtils.config().getNamespace()).isNotNull();
	}
	
}
