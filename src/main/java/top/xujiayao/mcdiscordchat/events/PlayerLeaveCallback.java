package top.xujiayao.mcdiscordchat.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * @author Xujiayao
 */
public interface PlayerLeaveCallback {

	Event<PlayerLeaveCallback> EVENT = EventFactory.createArrayBacked(PlayerLeaveCallback.class,
		  callbacks -> playerEntity -> {
			  for (PlayerLeaveCallback callback : callbacks) {
				  callback.onLeave(playerEntity);
			  }
		  });

	void onLeave(ServerPlayerEntity playerEntity);
}
