package io.gitee.xujiayao147.mcDiscordChatBridge.mixins;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.metadata.EntrypointMetadata;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Language;

/**
 * @author Xujiayao
 */
@Mixin(Language.class)
public abstract class MixinLanguage {

	@Shadow
	@Final
	private static Logger LOGGER;

	@Shadow
	public static void load(InputStream inputStream, BiConsumer<String, String> entryConsumer) {
	}

	@Shadow
	@Final
	private static Gson GSON;

	@Shadow
	@Final
	private static Pattern TOKEN_PATTERN;

	@SuppressWarnings("unchecked")
	@Redirect(method = "create", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap$Builder;build()Lcom/google/common/collect/ImmutableMap;"))
	private static <K, V> ImmutableMap<K, V> immutableBuild(Builder<K, V> builder) {
		ImmutableMap<K, V> immutableMap = builder.build();
		LinkedHashMap<String, String> map = new LinkedHashMap<>((ImmutableMap<String, String>) immutableMap);

		LOGGER.info("MC Discord Chat Bridge will now try to load modded language files.");
		AtomicInteger loadedFiles = new AtomicInteger();
		FabricLoader loader = FabricLoader.getInstance();
		loader.getAllMods().forEach(modContainer -> {
			ModMetadata metadata = modContainer.getMetadata();
			if (metadata instanceof LoaderModMetadata) {
				Optional<EntrypointMetadata> optional = ((LoaderModMetadata) metadata).getEntrypoints("main").stream()
						.findFirst();
				if (optional.isPresent()) {
					EntrypointMetadata entrypointMetadata = optional.get();
					try {
						InputStream inputStream = FabricLauncherBase.getClass(entrypointMetadata.getValue())
								.getResourceAsStream(
										"/assets/" + modContainer.getMetadata().getId() + "/lang/en_us.json");
						if (inputStream == null)
							return;
						Throwable var3 = null;
						try {
							JsonObject jsonObject = GSON.fromJson(
									new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);
							for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
								String string = TOKEN_PATTERN
										.matcher(JsonHelper.asString(entry.getValue(), entry.getKey()))
										.replaceAll("%$1s");
								map.put(entry.getKey(), string);
							}
							loadedFiles.getAndIncrement();
							LOGGER.info("Successfully loaded /assets/" + modContainer.getMetadata().getId()
									+ "/lang/en_us.json");
						} catch (Throwable var13) {
							var3 = var13;
							throw var13;
						} finally {
							if (var3 != null) {
								try {
									inputStream.close();
								} catch (Throwable var12) {
									var3.addSuppressed(var12);
								}
							} else {
								inputStream.close();
							}
						}
					} catch (JsonParseException | IOException var15) {
						LOGGER.error("Couldn't read strings from /assets/" + modContainer.getMetadata().getId()
								+ "/lang/en_us.json", var15);
					} catch (ClassNotFoundException ignored) {
					}
				}
			}
		});
		LOGGER.info("MC Discord Chat Bridge loaded " + loadedFiles.get() + " modded language files.");

		return (ImmutableMap<K, V>) ImmutableMap.builder().putAll(map).build();
	}

}
