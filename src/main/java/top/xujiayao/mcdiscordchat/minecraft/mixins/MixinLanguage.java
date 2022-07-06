//#if MC >= 11600
package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Language;
//#if MC >= 11800
import org.slf4j.Logger;
//#else
//$$ import org.apache.logging.log4j.Logger;
//#endif
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * @author Xujiayao
 */
@Mixin(Language.class)
public abstract class MixinLanguage {

	@Final
	@Shadow
	private static Logger LOGGER;

	@Final
	@Shadow
	private static Gson GSON;

	@Final
	@Shadow
	private static Pattern TOKEN_PATTERN;

	@SuppressWarnings({"unchecked", "mapping"})
	@Redirect(method = "create", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap$Builder;build()Lcom/google/common/collect/ImmutableMap;"))
	private static <K, V> ImmutableMap<K, V> build(Builder<K, V> builder) {
		LinkedHashMap<String, String> map = new LinkedHashMap<>((ImmutableMap<String, String>) builder.build());

		FabricLoader.getInstance().getAllMods().forEach(modContainer -> {
			Optional<? extends EntrypointMetadata> optional = ((LoaderModMetadata) modContainer.getMetadata()).getEntrypoints("main").stream().findFirst();
			if (optional.isPresent()) {
				try {
					try (InputStream inputStream = FabricLauncherBase.getClass(optional.get().getValue()).getResourceAsStream("/assets/" + modContainer.getMetadata().getId() + "/lang/en_us.json")) {
						if (inputStream == null) {
							return;
						}

						JsonObject json = GSON.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);
						for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
							String string = TOKEN_PATTERN.matcher(JsonHelper.asString(entry.getValue(), entry.getKey())).replaceAll("%$1s");
							map.put(entry.getKey(), string);
						}
					}
				} catch (ClassNotFoundException ignored) {
				} catch (Exception e) {
					LOGGER.error("Couldn't read strings from /assets/{}", modContainer.getMetadata().getId() + "/lang/en_us.json", e);
				}
			}
		});

		return (ImmutableMap<K, V>) ImmutableMap.builder().putAll(map).build();
	}
}
//#endif