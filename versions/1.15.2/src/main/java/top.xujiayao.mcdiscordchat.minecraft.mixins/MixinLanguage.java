package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.metadata.EntrypointMetadata;
import net.fabricmc.loader.metadata.LoaderModMetadata;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Language;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
	private static Pattern field_11489;

	@Final
	@Shadow
	private Map<String, String> translations;

	@SuppressWarnings("deprecation")
	@Inject(method = "<init>", at = @At(value = "RETURN"))
	private void Language(CallbackInfo ci) {
		FabricLoader.getInstance().getAllMods().forEach(modContainer -> {
			Optional<? extends EntrypointMetadata> optional = ((LoaderModMetadata) modContainer.getMetadata()).getEntrypoints("main").stream().findFirst();
			if (optional.isPresent()) {
				try {
					try (InputStream inputStream = FabricLauncherBase.getClass(optional.get().getValue()).getResourceAsStream("/assets/" + modContainer.getMetadata().getId() + "/lang/en_us.json")) {
						if (inputStream == null) {
							return;
						}

						JsonObject json = new Gson().fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);
						for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
							String string = field_11489.matcher(JsonHelper.asString(entry.getValue(), entry.getKey())).replaceAll("%$1s");
							translations.put(entry.getKey(), string);
						}
					}
				} catch (ClassNotFoundException ignored) {
				} catch (Exception e) {
					LOGGER.error("Couldn't read strings from /assets/{}", modContainer.getMetadata().getId() + "/lang/en_us.json", e);
				}
			}
		});
	}
}