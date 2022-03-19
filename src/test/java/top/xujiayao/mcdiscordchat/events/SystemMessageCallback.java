package top.xujiayao.mcdiscordchat.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * @author Xujiayao
 */
public interface SystemMessageCallback {

	Event<SystemMessageCallback> EVENT = EventFactory.createArrayBacked(SystemMessageCallback.class,
			callbacks -> (message) -> {
				for (SystemMessageCallback callback : callbacks) {
					callback.onSystemMessage(message);
				}
			});

	void onSystemMessage(String message);
}
