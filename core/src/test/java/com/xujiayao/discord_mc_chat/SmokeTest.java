package com.xujiayao.discord_mc_chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
	void versionIsResolvedFromTheBuildResource() {
		assertFalse(Constants.VERSION.isBlank(), "dmcc_version.txt must be expanded by processResources");
		assertFalse(Constants.VERSION.contains("$"), "the version placeholder must be expanded: " + Constants.VERSION);
	}
}
