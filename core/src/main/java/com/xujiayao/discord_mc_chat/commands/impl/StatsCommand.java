package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.utils.JsonUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Stats command implementation.
 *
 * @author Xujiayao
 */
public final class StatsCommand implements Command {

	private static StatsProvider provider;

	/**
	 * Creates a stats command instance.
	 */
	public StatsCommand() {
	}

	/**
	 * Gets the currently registered stats provider.
	 *
	 * @return Current stats provider, or {@code null} when Minecraft is not ready.
	 */
	public static StatsProvider getProvider() {
		return provider;
	}

	/**
	 * Registers the stats provider implementation.
	 *
	 * @param provider Stats provider implementation.
	 */
	public static void setProvider(StatsProvider provider) {
		StatsCommand.provider = provider;
	}

	/**
	 * Adds the default Minecraft namespace when the identifier has no colon.
	 *
	 * @param value Identifier value entered by the user.
	 * @return Canonical Minecraft namespaced identifier, or the original value when empty/null or already namespaced.
	 */
	public static String normalizeMinecraftNamespace(String value) {
		if (value == null || value.isBlank() || value.contains(":")) {
			return value;
		}

		return "minecraft:" + value;
	}

	/**
	 * Counts how many player stats files contain a value for the given stat.
	 *
	 * @param type Stat category/type.
	 * @param stat Stat name.
	 * @return Number of matching player entries.
	 */
	public static int countStatResultEntries(String type, String stat) {
		if (provider == null) {
			return 0;
		}

		try {
			provider.saveAll();
		} catch (Exception ignored) {
			return 0;
		}

		Path statsDir = provider.getStatsDirectory();
		if (statsDir == null || !Files.exists(statsDir) || !Files.isDirectory(statsDir)) {
			return 0;
		}

		int count = 0;

		try (Stream<Path> stream = Files.list(statsDir)) {
			for (Path p : stream.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.toList()) {
				String fileName = p.getFileName().toString();
				String uuidStr = fileName.substring(0, fileName.length() - 5);
				try {
					UUID.fromString(uuidStr);
					int value = JsonUtils.getStat(p, normalizeMinecraftNamespace(type), normalizeMinecraftNamespace(stat));
					if (value > 0) {
						count++;
					}
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
			return 0;
		}

		return count;
	}

	@Override
	public String name() {
		return "stats";
	}

	@Override
	public CommandArgument[] args() {
		return new CommandArgument[]{
				new CommandArgument() {
					@Override
					public String name() {
						return "type";
					}

					@Override
					public String description() {
						return I18nManager.getDmccTranslation("commands.stats.args_desc.type");
					}
				},
				new CommandArgument() {
					@Override
					public String name() {
						return "stat";
					}

					@Override
					public String description() {
						return I18nManager.getDmccTranslation("commands.stats.args_desc.stat");
					}
				}
		};
	}

	@Override
	public String description() {
		return I18nManager.getDmccTranslation("commands.stats.description");
	}

	@Override
	public void execute(CommandSender sender, String... args) {
		if (provider == null) {
			sender.reply(I18nManager.getDmccTranslation("commands.stats.server_not_ready"));
			return;
		}

		provider.saveAll(); // Ensure data is written to disk

		String type = normalizeMinecraftNamespace(args[0]);
		String stat = normalizeMinecraftNamespace(args[1]);

		Path statsDir = provider.getStatsDirectory();

		if (statsDir == null || !Files.exists(statsDir) || !Files.isDirectory(statsDir)) {
			sender.reply(I18nManager.getDmccTranslation("commands.stats.dir_not_found"));
			return;
		}

		Map<String, Integer> leaderboard = new ConcurrentHashMap<>();

		try (Stream<Path> stream = Files.list(statsDir)) {
			stream.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().endsWith(".json"))
					.forEach(p -> {
						String fileName = p.getFileName().toString();
						String uuidStr = fileName.substring(0, fileName.length() - 5);
						try {
							UUID uuid = UUID.fromString(uuidStr);
							int value = JsonUtils.getStat(p, normalizeMinecraftNamespace(type), normalizeMinecraftNamespace(stat));
							if (value > 0) {
								String name = provider.getPlayerName(uuid);
								if (name == null || name.isBlank()) {
									name = uuidStr;
								}
								leaderboard.put(name, value);
							}
						} catch (Exception ignored) {
						}
					});
		} catch (Exception e) {
			sender.reply(I18nManager.getDmccTranslation("commands.stats.read_failed", e.getMessage()));
			return;
		}

		if (leaderboard.isEmpty()) {
			sender.reply(I18nManager.getDmccTranslation("commands.stats.no_stats", type, stat));
			return;
		}

		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(leaderboard.entrySet());
		sorted.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

		int maxWidth = 4;
		if (!sorted.isEmpty()) {
			int maxValLen = String.valueOf(sorted.getFirst().getValue()).length();
			if (maxValLen > maxWidth) {
				maxWidth = maxValLen;
			}
		}

		String format = "%-" + maxWidth + "d %s";

		StringBuilder sb = new StringBuilder();
		String colon = I18nManager.getDmccTranslation("commands.stats.colon");

		sb.append("========== ").append(I18nManager.getDmccTranslation("commands.stats.stats")).append(" ==========\n\n")
				.append(I18nManager.getDmccTranslation("commands.stats.args_desc.type")).append(colon).append(type).append("\n")
				.append(I18nManager.getDmccTranslation("commands.stats.args_desc.stat")).append(colon).append(stat).append("\n\n");

		for (int i = 0; i < sorted.size(); i++) {
			Map.Entry<String, Integer> entry = sorted.get(i);
			sb.append(String.format(format, entry.getValue(), entry.getKey()));
			if (i < sorted.size() - 1) {
				sb.append("\n");
			}
		}

		sender.reply(sb.toString());
	}

	/**
	 * Abstraction for reading Minecraft statistics from runtime/storage.
	 */
	public interface StatsProvider {
		/**
		 * Flushes in-memory stats to disk before reading.
		 */
		void saveAll();

		/**
		 * Gets the stats directory path.
		 *
		 * @return Stats directory path.
		 */
		Path getStatsDirectory();

		/**
		 * Resolves a player name from UUID.
		 *
		 * @param uuid Player UUID.
		 * @return Player name, or {@code null} if unknown.
		 */
		String getPlayerName(UUID uuid);

		/**
		 * Gets available stat categories/types.
		 *
		 * @return Available stat type identifiers.
		 */
		List<String> getStatTypes();

		/**
		 * Gets available stat names for a category/type.
		 *
		 * @param type Stat category/type.
		 * @return Available stat names within the category.
		 */
		List<String> getStatNames(String type);
	}
}
