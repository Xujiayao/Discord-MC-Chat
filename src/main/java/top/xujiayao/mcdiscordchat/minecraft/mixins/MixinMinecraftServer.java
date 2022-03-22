package top.xujiayao.mcdiscordchat.minecraft.mixins;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Date;
import java.util.UUID;

import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.CONSOLE_LOG_CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.SIMPLE_DATE_FORMAT;
import static top.xujiayao.mcdiscordchat.Main.TEXTS;

/**
 * @author Xujiayao
 */
@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

	@Inject(method = "sendSystemMessage", at = @At("HEAD"))
	private void sendSystemMessage(Text message, UUID sender, CallbackInfo ci) {
		if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
			CONSOLE_LOG_CHANNEL.sendMessage(TEXTS.consoleLogMessage()
					.replace("%timestamp%", SIMPLE_DATE_FORMAT.format(new Date()))
					.replace("%message%", message.getString())).queue();
		}
	}
}
