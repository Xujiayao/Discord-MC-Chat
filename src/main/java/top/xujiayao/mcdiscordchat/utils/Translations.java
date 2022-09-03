package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import top.xujiayao.mcdiscordchat.Config;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class Translations {

	private static Map<String, String> translations;

	public static void init() {
		translations = new HashMap<>();

		Optional<Path> optional = FabricLoader.getInstance().getModContainer("mcdiscordchat").orElseThrow()
				.findPath("/lang/" + CONFIG.generic.language + ".json");

		if (optional.isPresent()) {
			try {
				String content = IOUtils.toString(Files.newInputStream(optional.get()), StandardCharsets.UTF_8);
				translations = new Gson().fromJson(content, new TypeToken<Map<String, String>>() {
				}.getType());
			} catch (Exception e) {
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}
		}
	}

	public static String translate(String key, Object... args) {
		return String.format(translations.get(key), args);
	}

	public static String translateMessage(String key) {
		try {
			Field field = Config.CustomMessage.class.getField(key.substring(8));
			String configValue = (String) field.get(new Config.CustomMessage());

			return configValue.isBlank() ? translations.get(key) : configValue;
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
			return translations.get(key);
		}
	}
}
