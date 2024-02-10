//#if MC >= 11600
package com.xujiayao.mcdiscordchat.minecraft.mixins;

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
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.xujiayao.mcdiscordchat.Main.LOGGER;

/**
 * @author Xujiayao
 */
@Mixin(Language.class)
public class MixinLanguage {

	@Final
	@Shadow
	private static Gson GSON;

	@Final
	@Shadow
	private static Pattern UNSUPPORTED_FORMAT_PATTERN;

	@ModifyVariable(method = "loadDefault()Lnet/minecraft/locale/Language;", at = @At("STORE"), ordinal = 0)
	private static Map<String, String> mapInjected(Map<String, String> originalMap) {
		Map<String, String> map = new HashMap<>(originalMap);

		FabricLoader.getInstance().getAllMods().forEach(modContainer -> {
			Optional<Path> optional = modContainer.findPath("/assets/" + modContainer.getMetadata().getId() + "/lang/en_us.json");

			if (optional.isPresent()) {
				try (InputStream inputStream = Files.newInputStream(optional.get())) {
					JsonObject json = GSON.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);
					for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
						String string = UNSUPPORTED_FORMAT_PATTERN.matcher(GsonHelper.convertToString(entry.getValue(), entry.getKey())).replaceAll("%$1s");
						map.put(entry.getKey(), string);
					}
				} catch (Exception e) {
					LOGGER.error("Couldn't read strings from /assets/{}", modContainer.getMetadata().getId() + "/lang/en_us.json", e);
				}
			}
		});

		return map;
	}
}
//#endif
