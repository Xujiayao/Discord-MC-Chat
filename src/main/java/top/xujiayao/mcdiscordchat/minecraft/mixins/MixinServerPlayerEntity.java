package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
//#if MC >= 11900
import net.minecraft.network.encryption.PlayerPublicKey;
//#endif
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
//#if MC < 11900
//$$ import org.apache.commons.lang3.exception.ExceptionUtils;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.xujiayao.mcdiscordchat.utils.Translations;

//#if MC < 11900
//$$ import java.lang.reflect.Method;
//#endif

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
//#if MC < 11900
//$$ import static top.xujiayao.mcdiscordchat.Main.LOGGER;
//#endif
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;

/**
 * @author Xujiayao
 */
@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity {

	//#if MC >= 11900
	private MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile profile, PlayerPublicKey publicKey) {
		super(world, pos, yaw, profile, publicKey);
	}
	//#elseif MC >= 11600
	//$$ private MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile profile) {
	//$$ 	super(world, pos, yaw, profile);
	//$$ }
	//#else
	//$$ private MixinServerPlayerEntity(World world, GameProfile profile) {
	//$$  super(world, profile);
	//$$ }
	//#endif

	@Inject(method = "onDeath", at = @At("HEAD"))
	private void onDeath(DamageSource source, CallbackInfo ci) {
		if (CONFIG.generic.announceDeathMessages) {
			//#if MC >= 11900
			TranslatableTextContent deathMessage = (TranslatableTextContent) getDamageTracker().getDeathMessage().getContent();
			String key = deathMessage.getKey();
			Object[] args = new String[deathMessage.getArgs().length];
			for (int i = 0; i < deathMessage.getArgs().length; i++) {
				args[i] = deathMessage.getArg(i).getString();
			}
			//#else
			//$$ TranslatableText deathMessage = (TranslatableText) getDamageTracker().getDeathMessage();
			//$$ String key = deathMessage.getKey();
			//$$ Object[] args = new String[deathMessage.getArgs().length];
			//$$ for (int i = 0; i < deathMessage.getArgs().length; i++) {
			//$$  try {
			//$$  	Method method = TranslatableText.class.getDeclaredMethod("getArg", int.class);
			//$$  	method.setAccessible(true);
			//$$  	method.invoke(deathMessage, i);
			//$$  } catch (Exception e) {
			//$$  	LOGGER.error(ExceptionUtils.getStackTrace(e));
			//$$  }
			//$$ }
			//#endif

			CHANNEL.sendMessage(Translations.translateMessage("message.deathMessage")
					.replace("%deathMessage%", MarkdownSanitizer.escape(Translations.translate(key, args)))).queue();
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.deathMessage")
						.replace("%deathMessage%", MarkdownSanitizer.escape(Translations.translate(key, args))));
			}
		}
	}
}
