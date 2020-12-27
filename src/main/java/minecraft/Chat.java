package minecraft;

import net.minecraft.client.network.ClientPlayerEntity;

/**
 * @author Xujiayao
 */
public class Chat {
	
	public static void execute(ClientPlayerEntity player, String msg) {
		player.sendChatMessage(msg);
	}
}
