package top.xujiayao.mcdiscordchat.minecraft.mixins;

import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Date;
//#if MC <= 11802
import java.util.UUID;
//#endif

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.CONSOLE_LOG_CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_LAST_RESET_TIME;
import static top.xujiayao.mcdiscordchat.Main.MINECRAFT_SEND_COUNT;
import static top.xujiayao.mcdiscordchat.Main.SIMPLE_DATE_FORMAT;
import static top.xujiayao.mcdiscordchat.Main.TEXTS;

/**
 * @author Xujiayao
 */
@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

	//#if MC >= 11900
	//$$ @Inject(method = "sendMessage", at = @At("HEAD"))
	//$$ private void sendMessage(Text message, CallbackInfo ci) {
	//#else
	@Inject(method = "sendSystemMessage", at = @At("HEAD"))
	private void sendSystemMessage(Text message, UUID sender, CallbackInfo ci) {
	//#endif
		if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
			if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20000) {
				MINECRAFT_SEND_COUNT = 0;
				MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
			}

			MINECRAFT_SEND_COUNT++;
			if (MINECRAFT_SEND_COUNT <= 20) {
				CONSOLE_LOG_CHANNEL.sendMessage(TEXTS.consoleLogMessage()
						.replace("%time%", SIMPLE_DATE_FORMAT.format(new Date()))
						.replace("%message%", MarkdownSanitizer.escape(message.getString()))).queue();
			}
		}
	}
}
