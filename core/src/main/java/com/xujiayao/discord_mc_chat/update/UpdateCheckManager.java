package com.xujiayao.discord_mc_chat.update;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.config.ModeManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.network.packets.EventPackets.MinecraftRelayPacket;
import com.xujiayao.discord_mc_chat.server.discord.DiscordManager;
import com.xujiayao.discord_mc_chat.server.message.DiscordMessageParser;
import com.xujiayao.discord_mc_chat.server.message.MinecraftMessageParser;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.HttpUtils;
import tools.jackson.databind.JsonNode;

import java.util.List;
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
	private static int autoNotificationCount = 3; // Only notify when this value % 4 == 0

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
				checkAndNotify(true);
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
	 *
	 * @return The check result.
	 */
	public static CheckResult checkNow() {
		try {
			return checkAndNotify(false);
		} catch (Exception e) {
			String message = I18nManager.getDmccTranslation("commands.update.check_failed", e.getMessage());
			for (String line : message.split("\n", -1)) {
				LOGGER.error(line);
			}
			return CheckResult.failure(message);
		}
	}

	private static CheckResult checkAndNotify(boolean auto) throws Exception {
		CheckResult result = check();

		if (auto && result.status() == CheckStatus.AVAILABLE) {
			if (!result.version().equals(lastAutoNotifiedVersion)) {
				lastAutoNotifiedVersion = result.version();
				autoNotificationCount = 3;
			}

			autoNotificationCount++;
			if (autoNotificationCount % 4 == 0) {
				notify(result);
			}
		} else if (result.status() == CheckStatus.AVAILABLE) {
			notify(result);
		}

		return result;
	}

	private static CheckResult check() throws Exception {
		JsonNode root = JSON_MAPPER.readTree(HttpUtils.getNoCache(UPDATE_URL));
		JsonNode versionsNode = root.path("versions");

		String minecraftVersion = resolveCurrentMinecraftVersion();
		if (!versionsNode.isArray()) {
			return CheckResult.noCompatible(minecraftVersion);
		}

		for (JsonNode versionNode : versionsNode) {
			if (!matchesMinecraftVersion(versionNode.path("compatibility"), minecraftVersion)) {
				continue;
			}

			String version = versionNode.path("version").asString("");
			String notes = resolveReleaseNotes(versionNode.path("notes"));
			String changelogUrl = versionNode.path("changelog_url").asString("");
			String downloadUrl = versionNode.path("download_url").asString("");
			long publishedAt = versionNode.path("published_at").asLong(0L);

			if (version.isBlank()) {
				continue;
			}

			if (version.equals(Constants.VERSION)) {
				return CheckResult.upToDate(version);
			}

			return CheckResult.available(version, notes, changelogUrl, downloadUrl, publishedAt);
		}

		return CheckResult.noCompatible(minecraftVersion);
	}

	private static String resolveCurrentMinecraftVersion() {
		if ("standalone".equals(ModeManager.getMode())) {
			JsonNode servers = ConfigManager.getConfigNode("multi_server.servers");
			if (servers != null && servers.isArray() && !servers.isEmpty()) {
				return servers.get(0).path("minecraft_version").asString("");
			}
			return "";
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

	private static void notify(CheckResult result) {
		String consoleMessage = DiscordMessageParser.formatDiscordTimestampsForPlainText(result.fullMessage());
		for (String line : consoleMessage.split("\n", -1)) {
			LOGGER.warn(line);
		}

		notifyDiscord(result.fullMessage());
		notifyMinecraft(ModeManager.getMode(), result.fullMessage());
	}

	private static void notifyDiscord(String message) {
		String primaryChannel = ConfigManager.getString("check_for_updates.channel", "");
		String fallbackChannel = ConfigManager.getString("broadcasts.minecraft_to_discord.player.chat", "");

		if (primaryChannel != null && !primaryChannel.isBlank()) {
			DiscordManager.sendBotMessage(primaryChannel, fallbackChannel, message);
		} else if (fallbackChannel != null && !fallbackChannel.isBlank()) {
			DiscordManager.sendBotMessage(fallbackChannel, message);
		}
	}

	private static void notifyMinecraft(String mode, String message) {
		MinecraftMessageParser.ParsedMessage parsedMessage = MinecraftMessageParser.parseSystemMessage(message, true);
		List<TextSegment> segments = parsedMessage.minecraftSegments();
		if (segments == null || segments.isEmpty()) {
			return;
		}

		MinecraftRelayPacket packet = new MinecraftRelayPacket(segments);
		if ("single_server".equals(mode)) {
			NetworkManager.sendPacketToClient(packet, "Internal");
		} else if ("standalone".equals(mode)) {
			NetworkManager.broadcastToClients(packet);
		}
	}

	private static String resolveReleaseNotes(JsonNode notesNode) {
		if (notesNode == null || notesNode.isMissingNode() || notesNode.isNull()) {
			return "";
		}

		String language = I18nManager.getLanguage();
		String notes = notesNode.path(language).asString("");
		if (notes.isBlank()) {
			notes = notesNode.path("en_us").asString("");
		}
		if (notes.isBlank()) {
			notes = notesNode.path("").asString("");
		}
		return notes;
	}

	private static String formatNotesForDiscord(String notes) {
		if (notes == null || notes.isBlank()) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		String[] lines = notes.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				builder.append('\n');
			}
			builder.append("> ").append(lines[i]);
		}
		return builder.toString();
	}

	private enum CheckStatus {
		AVAILABLE,
		UP_TO_DATE,
		NO_COMPATIBLE,
		FAILED
	}

	public record CheckResult(
			CheckStatus status,
			String message,
			String fullMessage,
			String version
	) {
		static CheckResult available(String version, String notes, String changelogUrl, String downloadUrl, long publishedAt) {
			String message = I18nManager.getDmccTranslation("commands.update.available_short", version);
			String fullMessage = I18nManager.getDmccTranslation(
					"commands.update.available_full",
					Constants.VERSION,
					version,
					publishedAt,
					publishedAt,
					downloadUrl,
					changelogUrl,
					formatNotesForDiscord(notes)
			).trim();
			return new CheckResult(CheckStatus.AVAILABLE, message, fullMessage, version);
		}

		static CheckResult upToDate(String version) {
			String message = I18nManager.getDmccTranslation("commands.update.up_to_date");
			return new CheckResult(CheckStatus.UP_TO_DATE, message, "", version);
		}

		static CheckResult noCompatible(String minecraftVersion) {
			String message = I18nManager.getDmccTranslation("commands.update.no_compatible", minecraftVersion);
			return new CheckResult(CheckStatus.NO_COMPATIBLE, message, "", "");
		}

		static CheckResult failure(String message) {
			return new CheckResult(CheckStatus.FAILED, message, "", "");
		}
	}
}
