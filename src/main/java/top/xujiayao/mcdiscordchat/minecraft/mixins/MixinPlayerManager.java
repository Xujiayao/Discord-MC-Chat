package top.xujiayao.mcdiscordchat.minecraft.mixins;

import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.xujiayao.mcdiscordchat.utils.MarkdownParser;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.MULTI_SERVER;
import static top.xujiayao.mcdiscordchat.Main.TEXTS;

/**
 * @author Xujiayao
 */
@Mixin(PlayerManager.class)
public class MixinPlayerManager {

	@Inject(method = "onPlayerConnect", at = @At("HEAD"))
	private void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
		CHANNEL.sendMessage(TEXTS.joinServer()
				.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName()))).queue();
		if (CONFIG.multiServer.enable) {
			MULTI_SERVER.sendMessage(MarkdownParser.parseMarkdown(TEXTS.joinServer()
					.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName()))));
		}
	}

	@Inject(method = "remove", at = @At("HEAD"))
	private void remove(ServerPlayerEntity player, CallbackInfo ci) {
		CHANNEL.sendMessage(TEXTS.leftServer()
				.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName()))).queue();
		if (CONFIG.multiServer.enable) {
			MULTI_SERVER.sendMessage(MarkdownParser.parseMarkdown(TEXTS.leftServer()
					.replace("%playerName%", MarkdownSanitizer.escape(player.getEntityName()))));
		}
	}
}

