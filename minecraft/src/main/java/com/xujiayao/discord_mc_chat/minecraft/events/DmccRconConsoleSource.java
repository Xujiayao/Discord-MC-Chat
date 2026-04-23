package com.xujiayao.discord_mc_chat.minecraft.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.rcon.RconConsoleSource;
import org.jspecify.annotations.NonNull;

/**
 * {@link RconConsoleSource} implementation that captures command output for DMCC
 * and preserves line breaks between messages.
 *
 * @author Xujiayao
 */
final class DmccRconConsoleSource extends RconConsoleSource {

	private final StringBuffer buffer = new StringBuffer();

	DmccRconConsoleSource(MinecraftServer server) {
		super(server);
	}

	@Override
	public void prepareForCommand() {
		buffer.setLength(0);
	}

	@NonNull
	@Override
	public String getCommandResponse() {
		return buffer.toString();
	}

	@Override
	public void sendSystemMessage(@NonNull Component message) {
		if (!buffer.isEmpty()) {
			buffer.append('\n');
		}
		buffer.append(message.getString());
	}
}
