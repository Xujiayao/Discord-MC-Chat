package top.xujiayao.mcdiscordchat.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.command.ServerCommandSource;

/**
 * @author Xujiayao
 */
public interface CommandExecutionCallback {
	Event<CommandExecutionCallback> EVENT = EventFactory.createArrayBacked(CommandExecutionCallback.class,
		  callbacks -> (command, source) -> {
			  for (CommandExecutionCallback callback : callbacks) {
				  callback.onExecuted(command, source);
			  }
		  });

	void onExecuted(String command, ServerCommandSource source);
}
