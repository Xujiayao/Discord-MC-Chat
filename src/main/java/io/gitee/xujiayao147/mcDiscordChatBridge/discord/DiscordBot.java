package io.gitee.xujiayao147.mcDiscordChatBridge.discord;

import javax.security.auth.login.LoginException;

import io.gitee.xujiayao147.mcDiscordChatBridge.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * @author Xujiayao
 */
public class DiscordBot extends ListenerAdapter {

	MessageChannel channel;

	Message msg;
	String formattedMsg;

	JDA jda;

	public void initialize() throws LoginException {
		String token = "NzkyNDIxOTQ3OTExODMxNTYy." + "X-decg." + "u7VRPDqSmOXHQm-_vkwDaVIqmEo";

		jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
				.addEventListeners(new DiscordBot()).setActivity(Activity.listening("主人敲键盘的声音~")).build();
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		try {
			msg = event.getMessage();

			if (msg.getChannel().getId().equals("792407823295184906")) {
				channel = msg.getChannel();

				formattedMsg = "<" + msg.getAuthor().getName() + "> " + msg.getContentDisplay();

				if (event.getAuthor() != event.getJDA().getSelfUser()) {
					Main.getSend().sendMcMessage(formattedMsg);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendMessage(String text) {
		channel = jda.getTextChannelById("792407823295184906");

		channel.sendMessage("[Minecraft] " + text).queue();
	}
}