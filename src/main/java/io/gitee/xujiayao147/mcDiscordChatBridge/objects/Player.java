package io.gitee.xujiayao147.mcDiscordChatBridge.objects;

/**
 * @author Xujiayao
 */
public class Player {

	private String name;
	private String uuid;
	private String expiresOn;

	public Player(String name, String uuid, String expiresOn) {
		this.name = name;
		this.uuid = uuid;
		this.expiresOn = expiresOn;
	}

	public String getName() {
		return name;
	}

	public String getUuid() {
		return uuid;
	}
}
