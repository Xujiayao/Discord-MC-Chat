package io.gitee.xujiayao147.mcDiscordChatBridge.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.gitee.xujiayao147.mcDiscordChatBridge.Main;
import net.dv8tion.jda.api.entities.Member;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;

/**
 * @author Xujiayao
 */
public class Utils {

	public static Pair<String, String> convertMentionsFromNames(String message) {

		if (!message.contains("@"))
			return new Pair<>(message, message);

		List<String> messageList = Arrays.asList(message.split("@[\\S]+"));
		if (messageList.size() == 0) {
			messageList = new ArrayList<>();
			messageList.add("");
		}

		StringBuilder discordString = new StringBuilder(), mcString = new StringBuilder();
		Pattern pattern = Pattern.compile("@[\\S]+");
		Matcher matcher = pattern.matcher(message);

		int x = 0;
		while (matcher.find()) {
			Member member = null;
			for (Member m : Main.textChannel.getMembers()) {
				String name = matcher.group().substring(1);
				if (m.getUser().getName().toLowerCase().equals(name.toLowerCase())
						|| (m.getNickname() != null && m.getNickname().toLowerCase().equals(name.toLowerCase()))) {
					member = m;
				}
			}
			if (member == null) {
				discordString.append(messageList.get(x)).append(matcher.group());
				mcString.append(messageList.get(x)).append(matcher.group());
			} else {
				discordString.append(messageList.get(x)).append(member.getAsMention());
				mcString.append(messageList.get(x)).append(Formatting.YELLOW.toString()).append("@")
						.append(member.getEffectiveName()).append(Formatting.WHITE.toString());
			}
			x++;
		}
		if (x < messageList.size()) {
			discordString.append(messageList.get(x));
			mcString.append(messageList.get(x));
		}
		return new Pair<>(discordString.toString(), mcString.toString());
	}
}
