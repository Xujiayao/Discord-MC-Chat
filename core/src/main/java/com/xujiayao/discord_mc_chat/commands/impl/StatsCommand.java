package com.xujiayao.discord_mc_chat.commands.impl;

import com.xujiayao.discord_mc_chat.commands.Command;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.utils.JsonUtils;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;

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
public class StatsCommand implements Command {

	private static StatsProvider provider;

	public static StatsProvider getProvider() {
		return provider;
	}

	public static void setProvider(StatsProvider provider) {
		StatsCommand.provider = provider;
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

		String type = args[0];
		String stat = args[1];

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
							int value = JsonUtils.getStat(p, type, stat);
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

		StringBuilder sb = new StringBuilder();
		String colon = I18nManager.getDmccTranslation("commands.stats.colon");

		sb.append("========== ").append(I18nManager.getDmccTranslation("commands.stats.stats")).append(" ==========\n\n")
				.append(I18nManager.getDmccTranslation("commands.stats.args_desc.type")).append(colon).append(type).append("\n")
				.append(I18nManager.getDmccTranslation("commands.stats.args_desc.stat")).append(colon).append(stat).append("\n\n");

		for (int i = 0; i < sorted.size(); i++) {
			Map.Entry<String, Integer> entry = sorted.get(i);
			sb.append(i + 1).append(". ").append(entry.getKey()).append(": ").append(entry.getValue());
			if (i < sorted.size() - 1) {
				sb.append("\n");
			}
		}

		sender.reply(sb.toString());
	}

	public interface StatsProvider {
		void saveAll();

		Path getStatsDirectory();

		String getPlayerName(UUID uuid);

		List<String> getStatTypes();

		List<String> getStatNames(String type);
	}
}
