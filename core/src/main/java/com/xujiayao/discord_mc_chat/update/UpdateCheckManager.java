package com.xujiayao.discord_mc_chat.update;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.HttpUtils;
import tools.jackson.databind.JsonNode;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles DMCC update checks and notification routing.
 *
 * @author Xujiayao
 */
public final class UpdateCheckManager {

	private static final String UPDATE_URL = "https://cdn.jsdelivr.net/gh/Xujiayao/Discord-MC-Chat@vv3/update/versions.json";
	private static final int AUTO_CHECK_INTERVAL_SECONDS = 21600;

	private static ScheduledExecutorService updateExecutor;
	private static ScheduledFuture<?> updateTask;

	private static String lastAutoNotifiedVersion;
	private static int autoNotificationXxxx = 0; // Only notify when this value % 4 == 0

	private UpdateCheckManager() {
	}

	/**
	 * Starts the automatic update checker.
	 */
	public static void start() {
		if ("multi_server_client".equals(ModeManager.getMode()) || !ConfigManager.getBoolean("check_for_updates.enable")) {
			return;
		}

		if (updateExecutor == null || updateExecutor.isShutdown()) {
			updateExecutor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "DMCC-UpdateCheck"));
		}

		if (updateTask != null) {
			updateTask.cancel(false);
		}

		updateTask = updateExecutor.scheduleWithFixedDelay(() -> {
			try {
				CheckResult result = check();
				// TODO
			} catch (Exception e) {
				LOGGER.error(I18nManager.getDmccTranslation("commands.update.check_failed", e.getMessage()));
			}
		}, 0, AUTO_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * Stops the automatic update checker.
	 */
	public static void shutdown() {
		if (updateTask != null) {
			updateTask.cancel(false);
			updateTask = null;
		}
		if (updateExecutor != null) {
			updateExecutor.shutdownNow();
			updateExecutor = null;
		}
	}

	/**
	 * Performs a manual update check.
	 */
	public static void checkNow() {
		try {
			CheckResult result = check();
			// TODO
		} catch (Exception e) {
			LOGGER.error(I18nManager.getDmccTranslation("commands.update.check_failed", e.getMessage()));
		}
	}

	private static CheckResult check() throws Exception {
		JsonNode root = JSON_MAPPER.readTree(HttpUtils.getNoCache(UPDATE_URL));
		JsonNode versionsNode = root.path("versions");

		String minecraftVersion = resolveCurrentMinecraftVersion();
		for (JsonNode versionNode : versionsNode) {
			if (!matchesMinecraftVersion(versionNode.path("compatibility"), minecraftVersion)) {
				continue;
			}

			String version = versionNode.path("version").asString();
			if (version.equals(Constants.VERSION)) {
				// TODO up to date
			}

			// TODO available
		}

		// TODO no compatible
	}

	private static String resolveCurrentMinecraftVersion() {
		if ("standalone".equals(ModeManager.getMode())) {
			JsonNode servers = ConfigManager.getConfigNode("multi_server.servers");
			return servers.get(0).path("minecraft_version").asString("");
		}

		return EnvironmentUtils.getMinecraftVersion();
	}

	private static boolean matchesMinecraftVersion(JsonNode compatibilityNode, String minecraftVersion) {
		if (!compatibilityNode.isArray() || minecraftVersion == null || minecraftVersion.isBlank()) {
			return false;
		}

		for (JsonNode compatibleVersion : compatibilityNode) {
			if (minecraftVersion.equals(compatibleVersion.asString())) {
				return true;
			}
		}
		return false;
	}

	private record CheckResult(
	) {
	}
}
