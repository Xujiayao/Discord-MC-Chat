package top.xujiayao.mcdiscordchat.minecraft.mixins;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
//#if MC >= 11900
import net.minecraft.network.encryption.PlayerPublicKey;
//#endif
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static top.xujiayao.mcdiscordchat.Main.TEXTS;

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
			CHANNEL.sendMessage(TEXTS.deathMessage()
					.replace("%deathMessage%", MarkdownSanitizer.escape(getDamageTracker().getDeathMessage().getString()))).queue();
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, false, false, null, TEXTS.deathMessage()
						.replace("%deathMessage%", MarkdownSanitizer.escape(getDamageTracker().getDeathMessage().getString())));
			}
		}
	}
}
