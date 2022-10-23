package top.xujiayao.mcdiscordchat.minecraft.mixins;

import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.xujiayao.mcdiscordchat.utils.Translations;
import top.xujiayao.mcdiscordchat.utils.Utils;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;

/**
 * @author Xujiayao
 */
@Mixin(PlayerManager.class)
public class MixinPlayerManager {

	@Inject(method = "onPlayerConnect", at = @At("RETURN"))
	private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
		Utils.setBotActivity();

		if (CONFIG.generic.announcePlayerJoinLeave) {
			CHANNEL.sendMessage(Translations.translateMessage("message.joinServer")
					.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName()))).queue();
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.joinServer")
						.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName())));
			}
		}
	}

	@Inject(method = "remove", at = @At("RETURN"))
	private void remove(ServerPlayerEntity player, CallbackInfo ci) {
		Utils.setBotActivity();

		if (CONFIG.generic.announcePlayerJoinLeave) {
			CHANNEL.sendMessage(Translations.translateMessage("message.leftServer")
					.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName()))).queue();
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.leftServer")
						.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName())));
			}
		}
	}
}

