package com.xujiayao.discord_mc_chat.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Abstraction for reading Minecraft statistics from the platform.
 * <p>
 * Implemented by the platform layer (it needs access to the running game) and consumed by
 * DMCC core's {@code stats} command and info reporting.
 *
 * @author Xujiayao
 */
public interface StatsProvider {

	/**
	 * Flushes in-memory stats to disk. Must be called on the game's main thread.
	 */
	void saveAll();

	Path getStatsDirectory();

	/**
	 * @return The player name, or {@code null} if the UUID is unknown.
	 */
	String getPlayerName(UUID uuid);

	List<String> getStatTypes();

	/**
	 * @param type Stat category/type, with or without the {@code minecraft:} namespace.
	 * @return Available stat names within the category.
	 */
	List<String> getStatNames(String type);

	/**
	 * Counts the players that have ever played on this server.
	 * <p>
	 * The platform answers this from its own player records, so core never has to know where Minecraft
	 * stores them or how they are keyed.
	 */
	int countPlayersEverJoined();
}
