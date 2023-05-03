package org.springframework.boot.bindings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class RegexTests {
	
	@Test
	public void testSvc() {
		String regex = ".*\\.svc.*";
		String input = "foo.bar.svc";
		assertThat(input.matches(regex)).isTrue();
	}

	@Test
	public void testSvcLocal() {
		String regex = ".*\\.svc.*";
		String input = "foo.bar.svc.cluster.local";
		assertThat(input.matches(regex)).isTrue();
	}
}
