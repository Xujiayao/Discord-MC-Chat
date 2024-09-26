package com.xujiayao.discord_mc_chat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.locale.Language;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.xujiayao.discord_mc_chat.Main.LOGGER;

/**
 * @author Xujiayao
 */
@Mixin(Language.class)
public class MixinLanguage {

	@Final
	@Shadow
	private static Pattern UNSUPPORTED_FORMAT_PATTERN;

	@Final
	@Shadow
	private Map<String, String> storage;

	@Inject(method = "<init>", at = @At(value = "RETURN"))
	private void Language(CallbackInfo ci) {
		FabricLoader.getInstance().getAllMods().forEach(modContainer -> {
			Optional<Path> optional = modContainer.findPath("/assets/" + modContainer.getMetadata().getId() + "/lang/en_us.json");

			if (optional.isPresent()) {
				try (InputStream inputStream = Files.newInputStream(optional.get())) {
					JsonObject json = new Gson().fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);
					for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
						String string = UNSUPPORTED_FORMAT_PATTERN.matcher(GsonHelper.convertToString(entry.getValue(), entry.getKey())).replaceAll("%$1s");
						storage.put(entry.getKey(), string);
					}
				} catch (Exception e) {
					LOGGER.error("Couldn't read strings from /assets/{}", modContainer.getMetadata().getId() + "/lang/en_us.json", e);
				}
			}
		});
	}
}
