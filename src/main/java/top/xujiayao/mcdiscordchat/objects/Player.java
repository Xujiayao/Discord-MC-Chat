package top.xujiayao.mcdiscordchat.objects;

/**
 * @author Xujiayao
 */
public class Player {

	private final String name;
	private final String uuid;

	public Player(String name, String uuid) {
		this.name = name;
		this.uuid = uuid;
	}

	public String getName() {
		return name;
	}

	public String getUuid() {
		return uuid;
	}
}
