package top.xujiayao.mcdiscordchat.mixins;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.xujiayao.mcdiscordchat.events.SystemMessageCallback;

import java.util.UUID;

/**
 * @author Xujiayao
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

	@Redirect(
			method = "sendSystemMessage",
			at = @At(
					value = "INVOKE",
					target = "Lorg/apache/logging/log4j/Logger;info(Ljava/lang/String;)V",
					remap = false
			)
	)
	private void onSystemMessage(Logger instance, String s, Text message, UUID senderUuid) {
		instance.info(message.getString());
		SystemMessageCallback.EVENT.invoker().onSystemMessage(message.getString());
	}
}
