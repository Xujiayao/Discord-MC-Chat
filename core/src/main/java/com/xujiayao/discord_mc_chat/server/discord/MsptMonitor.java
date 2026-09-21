package com.xujiayao.discord_mc_chat.server.discord;

import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.CommandPackets;
import com.xujiayao.discord_mc_chat.utils.ExecutorServiceUtils;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

public final class MsptMonitor {

	private static final int INFO_REQUEST_TIMEOUT_SECONDS = 3;
	private static final int INITIAL_CHECK_DELAY_SECONDS = 60;
	private static final int MAX_BACKOFF_SECONDS = 640;

	private static ScheduledExecutorService monitorExecutor;
	private static ScheduledFuture<?> monitorTask;

	// Tracks the current high-MSPT round (servers currently over threshold).
	private static Set<String> roundExceededServers = new LinkedHashSet<>();
	private static int backoffExponent = 0;

	private MsptMonitor() {
	}

	public static void start() {
		if (!ConfigManager.getBoolean("mspt_monitoring.enable")) {
			return;
		}

		// Create the executor while holding the lock, otherwise two concurrent start() calls could each create
		// one and leak the loser (a permanently alive scheduler thread that keeps reporting).
		ScheduledExecutorService staleExecutor = null;
		synchronized (MsptMonitor.class) {
			if (monitorExecutor == null || monitorExecutor.isShutdown()) {
				staleExecutor = monitorExecutor;
				monitorExecutor = Executors.newSingleThreadScheduledExecutor(ExecutorServiceUtils.newThreadFactory("DMCC-MsptMonitor"));
			}

			if (monitorTask != null) {
				monitorTask.cancel(false);
			}
			roundExceededServers = new LinkedHashSet<>();
			backoffExponent = 0;
			scheduleNextPoll(getBaseIntervalSeconds());
		}

		// The replaced executor can only be an already shut-down one here; shutdownNow() merely guarantees that
		// no task of it is still running. Done outside the lock so a stuck task cannot block start().
		if (staleExecutor != null) {
			staleExecutor.shutdownNow();
		}
	}

	private static void scheduleNextPoll(long delaySeconds) {
		if (monitorExecutor == null || monitorExecutor.isShutdown()) {
			return;
		}

		monitorTask = monitorExecutor.schedule(() -> {
			long nextDelay = getBaseIntervalSeconds();
			try {
				nextDelay = pollAndEvaluate();
			} catch (Exception e) {
				LOGGER.warn(I18nManager.getDmccTranslation("discord.manager.broadcast_failed", e.getMessage()));
			}
			scheduleNextPoll(Math.max(1, nextDelay));
		}, Math.max(1, delaySeconds), TimeUnit.SECONDS);
	}

	private static long pollAndEvaluate() {
		double threshold = ConfigManager.getDouble("mspt_monitoring.threshold", 50.0);
		int baseIntervalSeconds = getBaseIntervalSeconds();

		Map<String, CommandPackets.Info.ResponsePacket> infoMap = NetworkManager.requestInfoSnapshot(INFO_REQUEST_TIMEOUT_SECONDS);
		if (infoMap.isEmpty()) {
			return baseIntervalSeconds;
		}

		Set<String> graceServers = new HashSet<>();
		Map<String, CommandPackets.Info.ResponsePacket> eligibleInfoMap = new LinkedHashMap<>();
		for (Map.Entry<String, CommandPackets.Info.ResponsePacket> entry : infoMap.entrySet()) {
			if (NetworkManager.getClientConnectionAgeSeconds(entry.getKey()) < INITIAL_CHECK_DELAY_SECONDS) {
				graceServers.add(entry.getKey());
				continue;
			}

			eligibleInfoMap.put(entry.getKey(), entry.getValue());
		}

		Set<String> exceededNow = new HashSet<>();
		for (Map.Entry<String, CommandPackets.Info.ResponsePacket> entry : eligibleInfoMap.entrySet()) {
			CommandPackets.Info.ResponsePacket packet = entry.getValue();
			if (packet != null && packet.mspt > threshold) {
				exceededNow.add(entry.getKey());
			}
		}

		// Notifications are collected while the lock is held and dispatched after it is released, so that the
		// Discord/webhook network I/O of notifyMspt() never happens inside the critical section.
		List<PendingNotification> notifications = new ArrayList<>();
		long nextDelay;
		synchronized (MsptMonitor.class) {
			roundExceededServers.removeAll(graceServers);
			if (roundExceededServers.isEmpty()) {
				backoffExponent = 0;
			}

			if (roundExceededServers.isEmpty()) {
				if (exceededNow.isEmpty()) {
					// Nothing was over the threshold and nothing is now: no notification to collect.
					nextDelay = baseIntervalSeconds;
				} else {
					nextDelay = computeBackoffSeconds(baseIntervalSeconds, backoffExponent);
					long nextCheckEpochSeconds = Instant.now().plusSeconds(nextDelay).getEpochSecond();

					for (String server : exceededNow) {
						CommandPackets.Info.ResponsePacket packet = infoMap.get(server);
						if (packet != null) {
							notifications.add(new PendingNotification("first_exceeded", packet, nextCheckEpochSeconds));
						}
					}

					roundExceededServers = new LinkedHashSet<>(exceededNow);
					if (nextDelay < MAX_BACKOFF_SECONDS) {
						backoffExponent = 1;
					}
				}
			} else {
				Set<String> recovered = new HashSet<>(roundExceededServers);
				recovered.removeAll(exceededNow);

				if (exceededNow.isEmpty()) {
					// Every previously exceeded server recovered; the recovery notifications collected
					// below are still dispatched after the lock is released.
					roundExceededServers = new LinkedHashSet<>();
					backoffExponent = 0;
					nextDelay = baseIntervalSeconds;
				} else {
					nextDelay = computeBackoffSeconds(baseIntervalSeconds, backoffExponent);
				}

				// The delay computed above is the one scheduleNextPoll() will use, so the recovery
				// notifications can carry the same real next check time as the other notifications.
				long nextCheckEpochSeconds = Instant.now().plusSeconds(nextDelay).getEpochSecond();
				for (String server : recovered) {
					CommandPackets.Info.ResponsePacket packet = infoMap.get(server);
					if (packet != null) {
						notifications.add(new PendingNotification("first_recovered", packet, nextCheckEpochSeconds));
					}
				}

				if (!exceededNow.isEmpty()) {
					Set<String> stillExceeded = new HashSet<>(exceededNow);
					stillExceeded.retainAll(roundExceededServers);
					for (String server : stillExceeded) {
						CommandPackets.Info.ResponsePacket packet = infoMap.get(server);
						if (packet != null) {
							notifications.add(new PendingNotification("still_exceeded", packet, nextCheckEpochSeconds));
						}
					}

					Set<String> newlyExceeded = new HashSet<>(exceededNow);
					newlyExceeded.removeAll(roundExceededServers);
					for (String server : newlyExceeded) {
						CommandPackets.Info.ResponsePacket packet = infoMap.get(server);
						if (packet != null) {
							notifications.add(new PendingNotification("first_exceeded", packet, nextCheckEpochSeconds));
						}
					}

					roundExceededServers = new LinkedHashSet<>(exceededNow);
					if (nextDelay < MAX_BACKOFF_SECONDS) {
						backoffExponent += 1;
					}
				}
			}
		}

		// Message keys, per-server iteration order, thresholds and placeholder values are unchanged; only the
		// point in time at which the messages are sent moved out of the lock.
		for (PendingNotification notification : notifications) {
			notifyMspt(notification.messageKey(), notification.packet(), threshold, notification.nextCheckEpochSeconds());
		}
		return nextDelay;
	}

	private static void notifyMspt(String messageKey,
	                               CommandPackets.Info.ResponsePacket packet,
	                               double threshold,
	                               long nextCheckEpochSeconds) {
		JsonNode customMessages = I18nManager.getCustomMessages();
		if (customMessages == null) {
			return;
		}

		String template = customMessages.path("mspt_monitoring").path(messageKey).asString();
		if (template == null || template.isBlank()) {
			return;
		}

		String message = template
				.replace("{mspt}", "%.2f".formatted(packet.mspt))
				.replace("{threshold}", "%.2f".formatted(threshold))
				.replace("{next_check_time}", String.valueOf(nextCheckEpochSeconds));

		DiscordManager.sendMsptMonitoringMessage(packet.serverName, message);
	}

	private static int getBaseIntervalSeconds() {
		return Math.max(1, ConfigManager.getInt("mspt_monitoring.interval_seconds", 10));
	}

	private static long computeBackoffSeconds(int baseIntervalSeconds, int exponent) {
		long seconds = Math.max(1L, baseIntervalSeconds);
		for (int i = 0; i < exponent; i++) {
			seconds = Math.min(seconds * 2L, MAX_BACKOFF_SECONDS);
			if (seconds >= MAX_BACKOFF_SECONDS) {
				break;
			}
		}
		return Math.min(seconds, MAX_BACKOFF_SECONDS);
	}

	/**
	 * A notification collected under the monitor lock, to be dispatched after the lock is released.
	 */
	private record PendingNotification(String messageKey,
	                                   CommandPackets.Info.ResponsePacket packet,
	                                   long nextCheckEpochSeconds) {
	}

	public static void shutdown() {
		synchronized (MsptMonitor.class) {
			if (monitorTask != null) {
				monitorTask.cancel(false);
				monitorTask = null;
			}
			if (monitorExecutor != null) {
				ExecutorServiceUtils.shutdownAnExecutor(monitorExecutor);
				monitorExecutor = null;
			}
			roundExceededServers = new LinkedHashSet<>();
			backoffExponent = 0;
		}
	}
}
