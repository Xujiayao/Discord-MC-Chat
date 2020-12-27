package discord;

import javax.security.auth.login.LoginException;

import minecraft.Chat;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * @author Xujiayao
 */
public class Bot extends ListenerAdapter {
	
	MessageChannel channel;
	Message msg;
	String formattedMsg;
	
	public static void initialize() throws LoginException {
		String token = "NzkyNDIxOTQ3OTExODMxNTYy.X-decg.fxaKgCtGdd5SfixYD7nTXK_9wV8";
		
		JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
				.addEventListeners(new Bot()).setActivity(Activity.listening("主人敲键盘的声音~")).build();
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		try {
			msg = event.getMessage();
			
			formattedMsg = msg.getChannel().getName() + ": " + msg.getAuthor().getName() + ": " + msg.getContentDisplay();
			
			System.out.println(formattedMsg);
			
			if (event.getAuthor() != event.getJDA().getSelfUser()) {
				channel = event.getChannel();
				channel.sendMessage("我收到你发送的消息啦~\n"
						+ "\n"
						+ "> 来自频道：" + msg.getChannel().getName() + "\n"
						+ "> 来自用户：" + msg.getAuthor().getName() + "\n"
						+ "> 消息内容：" + msg.getContentDisplay()).queue();
				
				Chat.execute(getClientPlayer(), formattedMsg);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("resource")
	public ClientPlayerEntity getClientPlayer() {
	  return MinecraftClient.getInstance().player;
	}
}