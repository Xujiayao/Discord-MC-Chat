package com.xujiayao.discord_mc_chat.minecraft.events;

import com.xujiayao.discord_mc_chat.platform.StatsProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.stats.StatType;
import net.minecraft.world.level.storage.LevelResource;
import tools.jackson.databind.JsonNode;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.xujiayao.discord_mc_chat.Constants.JSON_MAPPER;

/**
 * Reads Minecraft player statistics on behalf of DMCC core.
 * <p>
 * This is the Minecraft side of {@link StatsProvider}. It stays in the game-side module because it needs
 * vanilla registries and the running server, while core only ever sees the interface.
 *
 * @author Xujiayao
 */
public final class MinecraftStatsProvider implements StatsProvider {

	/**
	 * Stat type and stat that every player accumulates, used as the "this player has played" marker.
	 */
	private static final String PLAY_TIME_TYPE = "minecraft:custom";
	private static final String PLAY_TIME_STAT = "minecraft:play_time";

	private final MinecraftServer server;

	public MinecraftStatsProvider(MinecraftServer server) {
		this.server = server;
	}

	@Override
	public void saveAll() {
		server.getPlayerList().saveAll();
	}

	@Override
	public Path getStatsDirectory() {
		return server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
	}

	@Override
	public String getPlayerName(UUID uuid) {
		return server.services().nameToIdCache()
				.get(uuid)
				.map(NameAndId::name)
				.orElse(null);
	}

	@Override
	public List<String> getStatTypes() {
		List<String> types = new ArrayList<>();
		for (Identifier loc : BuiltInRegistries.STAT_TYPE.keySet()) {
			types.add(loc.toString());
		}
		return types;
	}

	@Override
	public List<String> getStatNames(String type) {
		List<String> stats = new ArrayList<>();
		try {
			Identifier typeLoc = Identifier.parse(normalizeMinecraftNamespace(type));
			Optional<Holder.Reference<StatType<?>>> optional = BuiltInRegistries.STAT_TYPE.get(typeLoc);
			if (optional.isPresent()) {
				for (Identifier loc : optional.get().value().getRegistry().keySet()) {
					stats.add(loc.toString());
				}
			}
		} catch (Exception ignored) {
		}
		return stats;
	}

	@Override
	public int countPlayersEverJoined() {
		Path statsDir = getStatsDirectory();
		if (statsDir == null || !Files.isDirectory(statsDir)) {
			return 0;
		}

		int count = 0;
		try (Stream<Path> stream = Files.list(statsDir)) {
			for (Path file : stream.filter(Files::isRegularFile).toList()) {
				if (hasPlayTime(file)) {
					count++;
				}
			}
		} catch (Exception ignored) {
			return 0;
		}
		return count;
	}

	private static boolean hasPlayTime(Path file) {
		String fileName = file.getFileName().toString();
		if (!fileName.endsWith(".json")) {
			return false;
		}
		try {
			UUID.fromString(fileName.substring(0, fileName.length() - 5));
		} catch (IllegalArgumentException ignored) {
			// Not a player stats file.
			return false;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonNode typeNode = JSON_MAPPER.readTree(reader).path("stats").path(PLAY_TIME_TYPE);
			return !typeNode.isMissingNode() && typeNode.path(PLAY_TIME_STAT).asInt() > 0;
		} catch (Exception ignored) {
			return false;
		}
	}

	/**
	 * Adds the default Minecraft namespace when the identifier has no colon.
	 *
	 * @param value Identifier value entered by the user.
	 * @return The canonical namespaced identifier, unchanged when empty, null or already namespaced.
	 */
	private static String normalizeMinecraftNamespace(String value) {
		if (value == null || value.isBlank() || value.contains(":")) {
			return value;
		}
		return "minecraft:" + value;
	}
}
