package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Language;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

		if (optional.isEmpty()) {
			LOGGER.warn("-----------------------------------------");
			LOGGER.warn("MCDC cannot find translations for \"" + CONFIG.generic.language + "\" and uses \"en_us\" by default!");
			LOGGER.warn("");
			LOGGER.warn("You are welcome to contribute translations!");
			LOGGER.warn("Contributing: https://github.com/Xujiayao/MC-Discord-Chat#Contributing");
			LOGGER.warn("-----------------------------------------");

			optional = FabricLoader.getInstance().getModContainer("mcdiscordchat").orElseThrow()
					.findPath("/lang/en_us.json");
		}

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
		String translation1 = translations.get(key);
		if (translation1 != null) {
			return String.format(translation1, args);
		} else {
			//#if MC >= 11600
			String translation2 = Language.getInstance().get(key);
			//#else
			//$$ String translation2 = Language.getInstance().translate(key);
			//#endif
			if (!translation2.equals(key)) {
				return String.format(translation2, args);
			} else {
				return "TranslateError{\"key\":\"" + key + "\",\"args\":" + Arrays.toString(args) + "}";
			}
		}
	}

	public static String translateMessage(String key) {
		try {
			Field field = CONFIG.customMessage.getClass().getField(key.substring(8));
			String configValue = (String) field.get(CONFIG.customMessage);

			return configValue.isBlank() ? translations.get(key) : configValue;
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
			return translations.get(key);
		}
	}
}
