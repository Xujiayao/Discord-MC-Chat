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

public final class Constants {

	public static final boolean IS_MINECRAFT_ENV = EnvironmentUtils.isMinecraftEnvironment();

	public static final Logger LOGGER = new Logger();

	// YAML_MAPPER has to be initialized before VERSION because getDmccVersion() uses it.
	public static final ObjectMapper YAML_MAPPER = new YAMLMapper.Builder(new YAMLFactory())
			.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
			.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER).build();

	public static final ObjectMapper JSON_MAPPER = new ObjectMapper();
	public static final String VERSION = EnvironmentUtils.getDmccVersion();

	/**
	 * Shared HTTP client used for every outbound request.
	 * <p>
	 * Timeouts bound how long a caller can be blocked: 10 s to connect, 20 s to read and 30 s for the whole
	 * call. Everything else stays at the bare-client defaults (no response cache, default write timeout), so
	 * {@code OK_HTTP_CLIENT.cache()} remains {@code null}.
	 */
	public static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient.Builder()
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(20, TimeUnit.SECONDS)
			.callTimeout(30, TimeUnit.SECONDS)
			.build();

	public static final AtomicBoolean OVERWRITE_MINECRAFT_SOURCE_MESSAGES = new AtomicBoolean(false);

	private Constants() {
	}
}
