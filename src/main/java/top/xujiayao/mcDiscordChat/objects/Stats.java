package top.xujiayao.mcDiscordChat.objects;

/**
 * @author Xujiayao
 */
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
