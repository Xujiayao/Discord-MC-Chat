package com.xujiayao.discord_mc_chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.logging.Logger;
import okhttp3.OkHttpClient;

/**
 * Static final constants used across the project.
 *
 * @author Xujiayao
 */
public class Constants {

	public static final boolean IS_MINECRAFT_ENV = EnvironmentUtils.isMinecraftEnvironment();

	public static final Logger LOGGER = new Logger();

	// YAML_MAPPER has to be initialized before VERSION because getDmccVersion() uses it.
	public static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory()
			.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
			.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

	public static final String VERSION = EnvironmentUtils.getDmccVersion();

	public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	public static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient();
}
