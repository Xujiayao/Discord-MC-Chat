package com.xujiayao.discord_mc_chat.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.DetectedVersion;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
//#if MC >= 11900
import net.minecraft.network.chat.ComponentContents;
//#endif
import net.minecraft.network.chat.contents.TranslatableContents;
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

import static com.xujiayao.discord_mc_chat.Main.CONFIG;
import static com.xujiayao.discord_mc_chat.Main.HTTP_CLIENT;
import static com.xujiayao.discord_mc_chat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class Translations {

	private static Map<String, String> translations;

	public static void init() {
		translations = new HashMap<>();

		Optional<Path> optional = FabricLoader.getInstance().getModContainer("discord-mc-chat").orElseThrow()
				.findPath("/lang/" + CONFIG.generic.language + ".json");

		if (optional.isEmpty()) {
			LOGGER.warn("-----------------------------------------");
			LOGGER.warn("DMCC cannot find its translations for \"" + CONFIG.generic.language + "\" and uses \"en_us\" by default!");
			LOGGER.warn("");
			LOGGER.warn("You are welcome to contribute translations!");
			LOGGER.warn("Contributing: https://github.com/Xujiayao/Discord-MC-Chat#Contributing");
			LOGGER.warn("-----------------------------------------");

			optional = FabricLoader.getInstance().getModContainer("discord-mc-chat").orElseThrow()
					.findPath("/lang/en_us.json");
		}

		if (optional.isPresent()) {
			try {
				String dmccLang = IOUtils.toString(Files.newInputStream(optional.get()), StandardCharsets.UTF_8);
				translations.putAll(new Gson().fromJson(dmccLang, new TypeToken<Map<String, String>>() {
				}.getType()));

				File langFolder = new File(FabricLoader.getInstance().getConfigDir().toFile(), "/discord-mc-chat/");
				if (!(langFolder.mkdirs() || langFolder.isDirectory())) {
					return;
				}

				File minecraftLangFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "/discord-mc-chat/" + DetectedVersion.tryDetectVersion().name() + "-" + CONFIG.generic.language + ".json");

				if (minecraftLangFile.length() == 0) {
					Request request1 = new Request.Builder()
							.url("https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@" + DetectedVersion.tryDetectVersion().name() + "/assets/minecraft/lang/" + CONFIG.generic.language + ".json")
							.cacheControl(CacheControl.FORCE_NETWORK)
							.build();

					try (Response response1 = HTTP_CLIENT.newCall(request1).execute()) {
						if (response1.body() != null && response1.code() == 200) {
							BufferedSink sink = Okio.buffer(Okio.sink(minecraftLangFile));
							sink.writeAll(response1.body().source());
							sink.close();
						} else if (response1.code() == 404) {
							minecraftLangFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "/discord-mc-chat/latest-" + CONFIG.generic.language + ".json");

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
		for (int i = 0; i < args.length; i++) {
			Object object = args[i];
			if (object instanceof Component component) {
				//#if MC >= 11900
				ComponentContents componentContents = component.getContents();
				if (componentContents instanceof TranslatableContents translatable) {
					args[i] = translate(translatable.getKey(), translatable.getArgs());
				//#else
				//$$ if (component instanceof TranslatableComponent translatable) {
				//$$ 	args[i] = translate(translatable.getKey(), translatable.getArgs());
				//#endif
				} else {
					args[i] = component.getString();
				}
			} else {
				args[i] = object == null ? "null" : object.toString();
			}
		}

		String translation1 = translations.get(key);
		if (translation1 != null) {
			return String.format(translation1, args);
		} else {
			String translation2 = Language.getInstance().getOrDefault(key);
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
