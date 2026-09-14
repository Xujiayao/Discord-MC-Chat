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
