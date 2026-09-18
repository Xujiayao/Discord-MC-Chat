package com.xujiayao.discord_mc_chat;

import org.junit.jupiter.api.Test;

/**
 * Proves that the Gradle test wiring of the core module is functional.
 * <p>
 * DMCC's temporary refactoring tests are written per round and deleted before each delivery; this one
 * stays behind so {@code ./gradlew :core:test} keeps verifying that the test source set runs and that
 * the build-time resource expansion ({@code dmcc_version.txt}) actually reaches the runtime classpath.
 *
 * @author Xujiayao
 */
class SmokeTest {

	@Test
	void version() {
		System.out.println("Compiling DMCC Version: " + Constants.VERSION);
	}
}
