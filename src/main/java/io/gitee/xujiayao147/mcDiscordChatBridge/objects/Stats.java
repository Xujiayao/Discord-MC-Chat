package io.gitee.xujiayao147.mcDiscordChatBridge.objects;

public class Stats {

	private final String name;
	private final String content;

	public Stats(String name, String content) {
		this.name = name;
		this.content = content;
	}

	public String getName() {
		return name;
	}

	public String getContent() {
		return content;
	}
}
