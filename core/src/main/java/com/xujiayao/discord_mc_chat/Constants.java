package com.xujiayao.discord_mc_chat;

import com.xujiayao.discord_mc_chat.logging.Logger;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import okhttp3.OkHttpClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Static final constants used across the project.
 *
 * @author Xujiayao
 */
public final class Constants {

	/**
	 * Whether the current runtime is inside Minecraft.
	 */
	public static final boolean IS_MINECRAFT_ENV = EnvironmentUtils.isMinecraftEnvironment();

	/**
	 * Global logger used by all DMCC modules.
	 */
	public static final Logger LOGGER = new Logger();

	// YAML_MAPPER has to be initialized before VERSION because getDmccVersion() uses it.
	/**
	 * Shared YAML mapper configured for DMCC config files.
	 */
	public static final ObjectMapper YAML_MAPPER = new YAMLMapper.Builder(new YAMLFactory())
			.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
			.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER).build();

	/**
	 * Shared JSON mapper.
	 */
	public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	/**
	 * Current DMCC version string.
	 */
	public static final String VERSION = EnvironmentUtils.getDmccVersion();

	/**
	 * Shared OkHttp client instance.
	 * <p>
	 * Timeouts are set explicitly rather than relying on the library defaults, because every caller is on a
	 * latency-sensitive path (player-name lookups during chat parsing, the update check behind
	 * {@code /dmcc update}) and must not be able to stall a chat message or a Netty IO thread.
	 */
	public static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient.Builder()
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.writeTimeout(15, TimeUnit.SECONDS)
			.callTimeout(20, TimeUnit.SECONDS)
			.build();

	/**
	 * Whether Minecraft source messages should be overwritten by DMCC formatting.
	 * <p>
	 * For DMCC Client use.
	 */
	public static final AtomicBoolean OVERWRITE_MINECRAFT_SOURCE_MESSAGES = new AtomicBoolean(false);

	private Constants() {
	}
}
