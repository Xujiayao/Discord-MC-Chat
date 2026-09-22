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
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import com.xujiayao.discord_mc_chat.utils.HttpUtils;
import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;
import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * @author Xujiayao
 */
public final class UpdateCheckManager {

	private static final String UPDATE_URL = "https://cdn.jsdelivr.net/gh/Xujiayao/Discord-MC-Chat@vv3/update/versions.json";
	private static final int AUTO_CHECK_INTERVAL_SECONDS = 21600;

	private static ScheduledExecutorService updateExecutor;
	private static ScheduledFuture<?> updateTask;

	// Bumped by every start() and shutdown(). A task only notifies while the generation it was scheduled
	// under is still current: cancel(false) cannot interrupt a check that is already running, so without
	// this a check left over from before a reload could still broadcast a notification.
	private static volatile int updateGeneration;

	private static String lastAutoNotifiedVersion;
	private static int autoNotificationCount = 3; // Only notify when this value % 4 == 0

	private UpdateCheckManager() {
	}

	public static void start() {
		if ("multi_server_client".equals(ModeManager.getMode()) || !ConfigManager.getBoolean("check_for_updates.enable")) {
			return;
		}

		// New generation: the tasks of the previous one may still be running and must no longer notify.
		updateGeneration++;

		if (updateExecutor == null || updateExecutor.isShutdown()) {
			// Use the shared factory so the thread inherits the Mod ClassLoader (required under Fabric).
			updateExecutor = Executors.newSingleThreadScheduledExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-UpdateCheck"));
		}

		if (updateTask != null) {
			updateTask.cancel(false);
		}

		updateTask = updateExecutor.scheduleWithFixedDelay(() -> {
			int generation = updateGeneration;
			if (generation != updateGeneration) {
				// Superseded by a newer start()/shutdown() while this task was being dispatched.
				return;
			}
			try {
				// checkAndNotify() itself only notifies when the generation is still current.
				checkAndNotify(true, generation);
			} catch (Exception e) {
				if (generation == updateGeneration) {
					LOGGER.error(I18nManager.getDmccTranslation("commands.update.check_failed", e.getMessage()));
				}
			}
		}, 0, AUTO_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	public static void shutdown() {
		// Invalidate every task already scheduled, so none of them can notify after the shutdown.
		updateGeneration++;
		if (updateTask != null) {
			updateTask.cancel(false);
			updateTask = null;
		}
		if (updateExecutor != null) {
			updateExecutor.shutdownNow();
			updateExecutor = null;
		}
	}

	public static CheckResult checkNow() {
		try {
			return checkAndNotify(false, updateGeneration);
		} catch (Exception e) {
			String message = I18nManager.getDmccTranslation("commands.update.check_failed", e.getMessage());
			for (String line : message.split("\n", -1)) {
				LOGGER.error(line);
			}
			return CheckResult.failure(message);
		}
	}

	private static CheckResult checkAndNotify(boolean auto, int generation) throws Exception {
		CheckResult result = check();

		if (auto && generation != updateGeneration) {
			// The scheduler was restarted or shut down while this check was running: report the result to
			// the caller, but stay silent so the reload does not produce a notification from a dead task.
			return result;
		}

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

		return findNewestCompatibleRelease(versionsNode, minecraftVersion);
	}

	/**
	 * Picks the release a compatible Minecraft version should be told about.
	 *
	 * @param versionsNode     The {@code versions} array of the update manifest.
	 * @param minecraftVersion The Minecraft version that has to be compatible.
	 * @return The result describing the newest compatible release, an up-to-date result for the running
	 * version, or a no-compatible-release result.
	 */
	private static CheckResult findNewestCompatibleRelease(JsonNode versionsNode, String minecraftVersion) {
		// versions.json is not ordered, so a compatible entry is not necessarily an update: keep the newest
		// entry that is also newer than the running build and only report it once every entry was seen.
		String newestVersion = null;
		String newestNotes = null;
		String newestChangelogUrl = null;
		String newestDownloadUrl = null;
		long newestPublishedAt = 0L;

		for (JsonNode versionNode : versionsNode) {
			if (!matchesMinecraftVersion(versionNode.path("compatibility"), minecraftVersion)) {
				continue;
			}

			String version = versionNode.path("version").asString("");
			if (version.isBlank()) {
				continue;
			}

			if (version.equals(Constants.VERSION)) {
				return CheckResult.upToDate(version);
			}

			if (compareVersionNumbers(version, Constants.VERSION) <= 0
					|| (newestVersion != null && compareVersionNumbers(version, newestVersion) <= 0)) {
				continue;
			}

			newestVersion = version;
			newestNotes = resolveReleaseNotes(versionNode.path("notes"));
			newestChangelogUrl = versionNode.path("changelog_url").asString("");
			newestDownloadUrl = versionNode.path("download_url").asString("");
			newestPublishedAt = versionNode.path("published_at").asLong(0L);
		}

		if (newestVersion != null) {
			return CheckResult.available(newestVersion, newestNotes, newestChangelogUrl, newestDownloadUrl, newestPublishedAt);
		}

		return CheckResult.noCompatible(minecraftVersion);
	}

	/**
	 * Compares two DMCC version strings of the form {@code X.Y.Z} or {@code X.Y.Z-pre.N} segment by segment.
	 * The release list is published in an arbitrary order, so the update check needs an order of its own.
	 * <p>
	 * Both strings are split on {@code .} and {@code -}; each segment is compared numerically when both sides
	 * are digits, otherwise as text, and a pre-release segment always sorts before a release segment. That
	 * gives {@code 2.7.1 < 3.0.0-alpha.1 < 3.0.0-beta.0 < 3.0.0-beta.3 < 3.0.0}, which is the convention the
	 * project's tags follow. Build metadata ({@code +...}) carries no precedence and is ignored.
	 *
	 * @param a First version string.
	 * @param b Second version string.
	 * @return A negative value when {@code a} is older than {@code b}, 0 when they are equal, a positive value otherwise.
	 */
	private static int compareVersionNumbers(String rawA, String rawB) {
		String a = stripBuildMetadata(rawA);
		String b = stripBuildMetadata(rawB);
		String[] partsA = a.split("[.+-]");
		String[] partsB = b.split("[.+-]");
		int shared = Math.min(partsA.length, partsB.length);

		for (int i = 0; i < shared; i++) {
			String partA = partsA[i];
			String partB = partsB[i];
			boolean numericA = isNumericSegment(partA);
			boolean numericB = isNumericSegment(partB);

			int comparison;
			if (numericA && numericB) {
				comparison = Long.compare(Long.parseLong(partA), Long.parseLong(partB));
			} else if (numericA != numericB) {
				// A pre-release marker sorts before the release it belongs to.
				comparison = isPrereleaseSegment(a, partA) ? -1 : (isPrereleaseSegment(b, partB) ? 1 : partA.compareTo(partB));
			} else {
				comparison = partA.compareTo(partB);
			}

			if (comparison != 0) {
				return comparison;
			}
		}

		if (partsA.length == partsB.length) {
			return 0;
		}

		// One string ran out of segments: 3.0.0 still beats 3.0.0-beta.3, while 3.0.0.0 equals 3.0.0.
		String next = partsA.length > partsB.length ? partsA[shared] : partsB[shared];
		if (next.isEmpty()) {
			return 0;
		}

		return isPrereleaseSegment(partsA.length > partsB.length ? a : b, next)
				? (partsA.length > partsB.length ? -1 : 1)
				: (partsA.length > partsB.length ? 1 : -1);
	}

	/**
	 * Drops the {@code +build} part of a version string, which carries no precedence in the version order.
	 *
	 * @param version The full version string.
	 * @return The version string without its build metadata.
	 */
	private static String stripBuildMetadata(String version) {
		int buildIndex = version.indexOf('+');
		return buildIndex < 0 ? version : version.substring(0, buildIndex);
	}

	/**
	 * Tells whether a segment that follows the shared {@code .}/{@code -} segments is a pre-release marker,
	 * by checking the separator that introduced it.
	 *
	 * @param version   The full version string the segment came from.
	 * @param segment   The segment to classify.
	 * @return True when the segment starts a pre-release part of the version.
	 */
	private static boolean isPrereleaseSegment(String version, String segment) {
		int index = version.indexOf(segment);
		return index > 0 && version.charAt(index - 1) == '-';
	}

	private static boolean isNumericSegment(String segment) {
		if (segment.isEmpty()) {
			return false;
		}

		for (int i = 0; i < segment.length(); i++) {
			if (!Character.isDigit(segment.charAt(i))) {
				return false;
			}
		}
		return true;
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
		return notes;
	}

	private static String formatNotesForDiscord(String notes) {
		if (notes == null || notes.isBlank()) {
			return "";
		}

		return Arrays.stream(notes.split("\n", -1))
				.map(line -> "> " + line)
				.collect(Collectors.joining("\n"));
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
