package top.xujiayao.mcdiscordchat.mixins;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.xujiayao.mcdiscordchat.events.SystemMessageCallback;

import java.util.UUID;

/**
 * @author Xujiayao
 */
@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

	@Inject(method = "sendSystemMessage", at = @At("HEAD"))
	private void onSystemMessage(Text message, UUID sender, CallbackInfo ci) {
		SystemMessageCallback.EVENT.invoker().onSystemMessage(message.getString());
	}
}
