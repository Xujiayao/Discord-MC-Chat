package com.xujiayao.discord_mc_chat.common;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * @author Xujiayao
 */
public class Discord extends ListenerAdapter {

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		Message message = event.getMessage();
		String content = message.getContentRaw();
		User author = event.getAuthor();

		// Ignore bot messages
		if (author.isBot()) return;

		if (content.equalsIgnoreCase("!ping")) {
			event.getChannel().sendMessage("Pong!").queue();
		}
	}
}
