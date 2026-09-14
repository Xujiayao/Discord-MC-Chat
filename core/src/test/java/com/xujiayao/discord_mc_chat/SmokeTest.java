package com.xujiayao.discord_mc_chat;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.platform.Platform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that the Gradle test wiring of the core module is functional and guards the two
 * contracts that every other module depends on.
 * <p>
 * This test is intentionally permanent: DMCC's temporary refactoring tests are deleted before
 * each delivery, and this one stays behind so {@code ./gradlew :core:test} keeps meaning something.
 *
 * @author Xujiayao
 */
class SmokeTest {

	@Test
	void coreStartsWithTheNoopPlatformHost() {
		assertFalse(Platform.isAvailable(), "no platform should be registered before DMCC.init()");
		assertEquals("standalone", Platform.host().name());
		assertEquals(null, Platform.host().stats(), "the no-op host supplies no statistics");
	}

	@Test
	void onlySupportedModesAreAccepted() {
		assertTrue(ConfigManager.isValidMode("single_server"));
		assertTrue(ConfigManager.isValidMode("multi_server_client"));
		assertTrue(ConfigManager.isValidMode("standalone"));

		assertFalse(ConfigManager.isValidMode(""));
		assertFalse(ConfigManager.isValidMode("SINGLE_SERVER"));
		assertFalse(ConfigManager.isValidMode("nonsense"));
	}

	@Test
	void standaloneIsTheEnvironmentDefaultOutsideMinecraft() {
		assertFalse(Constants.IS_MINECRAFT_ENV, "unit tests never run inside Minecraft");
		assertEquals("standalone", ConfigManager.getDefaultMode());
	}

	@Test
	void versionIsResolvedFromTheBuildResource() {
		assertFalse(Constants.VERSION.isBlank(), "dmcc_version.txt must be expanded by processResources");
		assertFalse(Constants.VERSION.contains("$"), "the version placeholder must be expanded: " + Constants.VERSION);
	}
}
