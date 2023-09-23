package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.util.Language;
import okhttp3.CacheControl;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.HTTP_CLIENT;
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
				String mcdcLang = IOUtils.toString(Files.newInputStream(optional.get()), StandardCharsets.UTF_8);
				translations.putAll(new Gson().fromJson(mcdcLang, new TypeToken<Map<String, String>>() {
				}.getType()));

				File langFolder = new File(FabricLoader.getInstance().getConfigDir().toFile(), "/mcdiscordchat/");
				if (!(langFolder.mkdirs() || langFolder.isDirectory())) {
					return;
				}

				File minecraftLangFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "/mcdiscordchat/" + SharedConstants.getGameVersion().getName() + "-" + CONFIG.generic.language + ".json");

				if (minecraftLangFile.length() == 0) {
					Request request1 = new Request.Builder()
							.url("https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@" + SharedConstants.getGameVersion().getName() + "/assets/minecraft/lang/" + CONFIG.generic.language + ".json")
							.cacheControl(CacheControl.FORCE_NETWORK)
							.build();

					try (Response response1 = HTTP_CLIENT.newCall(request1).execute()) {
						if (response1.body() != null && response1.code() == 200) {
							BufferedSink sink = Okio.buffer(Okio.sink(minecraftLangFile));
							sink.writeAll(response1.body().source());
							sink.close();
						} else if (response1.code() == 404) {
							minecraftLangFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "/mcdiscordchat/latest-" + CONFIG.generic.language + ".json");

							Request request2 = new Request.Builder()
									.url("https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@latest/assets/minecraft/lang/" + CONFIG.generic.language + ".json")
									.cacheControl(CacheControl.FORCE_NETWORK)
									.build();

							try (Response response2 = HTTP_CLIENT.newCall(request2).execute()) {
								if (response2.body() != null && response2.code() == 200) {
									BufferedSink sink = Okio.buffer(Okio.sink(minecraftLangFile));
									sink.writeAll(response2.body().source());
									sink.close();
								}
							}
						}
					}
				}

				String minecraftLang = IOUtils.toString(minecraftLangFile.toURI(), StandardCharsets.UTF_8);
				translations.putAll(new Gson().fromJson(minecraftLang, new TypeToken<Map<String, String>>() {
				}.getType()));
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
