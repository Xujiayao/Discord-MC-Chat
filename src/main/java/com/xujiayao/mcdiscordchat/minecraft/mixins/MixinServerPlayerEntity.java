package com.xujiayao.mcdiscordchat.minecraft.mixins;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
//#if MC >= 11900 && MC < 11903
//$$ import net.minecraft.network.encryption.PlayerPublicKey;
//#endif
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
//#if MC >= 11600
import net.minecraft.util.math.BlockPos;
//#endif
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.xujiayao.mcdiscordchat.utils.Translations;

import java.util.Objects;

import static com.xujiayao.mcdiscordchat.Main.CHANNEL;
import static com.xujiayao.mcdiscordchat.Main.CONFIG;
import static com.xujiayao.mcdiscordchat.Main.MULTI_SERVER;

/**
 * @author Xujiayao
 */
@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity {

	//#if MC >= 11900 && MC < 11903
	//$$ private MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile profile, PlayerPublicKey publicKey) {
	//$$ 	super(world, pos, yaw, profile, publicKey);
	//$$ }
	//#elseif MC >= 11600
	protected MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}
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
			//#else
			//$$ TranslatableText deathMessage = (TranslatableText) getDamageTracker().getDeathMessage();
			//#endif
			String key = deathMessage.getKey();
			Object[] args = new String[deathMessage.getArgs().length];
			for (int i = 0; i < deathMessage.getArgs().length; i++) {
				Object object = deathMessage.getArgs()[i];
				if (object instanceof Text text) {
					args[i] = text.getString();
				} else {
					args[i] = object == null ? "null" : object.toString();
				}
			}

			CHANNEL.sendMessage(Translations.translateMessage("message.deathMessage")
					.replace("%deathMessage%", MarkdownSanitizer.escape(Translations.translate(key, args)))
					//#if MC >= 12003
					.replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(this.getDisplayName()).getString()))).queue();
					//#else
					//$$ .replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(this.getDisplayName()).getString()))).queue();
					//#endif
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.deathMessage")
						.replace("%deathMessage%", MarkdownSanitizer.escape(Translations.translate(key, args)))
						//#if MC >= 12003
						.replace("%playerName%", MarkdownSanitizer.escape(this.getDisplayName().getString())));
						//#else
						//$$ .replace("%playerName%", MarkdownSanitizer.escape(this.getDisplayName().getString())));
						//#endif
			}
		}
	}
}
