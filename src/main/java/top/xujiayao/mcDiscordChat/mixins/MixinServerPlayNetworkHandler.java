package top.xujiayao.mcDiscordChat.mixins;

import net.minecraft.network.MessageType;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.xujiayao.mcDiscordChat.events.ServerChatCallback;

import java.util.Optional;

/**
 * @author Xujiayao
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class MixinServerPlayNetworkHandler {

	@Shadow
	public ServerPlayerEntity player;

	@Final
	@Shadow
	private MinecraftServer server;

	@Shadow
	private int messageCooldown;

	@Shadow
	public abstract void sendPacket(Packet<?> packet);

	@Shadow
	public abstract void disconnect(Text reason);

	@Shadow
	protected abstract void executeCommand(String input);

	@Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcastChatMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V"), method = "method_31286", cancellable = true)
	private void onGameMessage(String string, CallbackInfo ci) {
		String message = StringUtils.normalizeSpace(string);
		Text text = new TranslatableText("chat.type.text", this.player.getDisplayName(), message);
		Optional<Text> eventResult = ServerChatCallback.EVENT.invoker().onServerChat(this.player, message, text);

		if (eventResult.isPresent()) {
			this.server.getPlayerManager().broadcastChatMessage(eventResult.get(), MessageType.CHAT, this.player.getUuid());
			ci.cancel();
		}
	}
}
