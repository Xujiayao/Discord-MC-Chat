package top.xujiayao.mcdiscordchat;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Xujiayao
 */
public class Config {

	// More information + Docs: https://blog.xujiayao.top/posts/4ba0a17a/

	public Generic generic = new Generic();
	public MultiServer multiServer = new MultiServer();
	public TextsZH textsZH = new TextsZH();
	public TextsEN textsEN = new TextsEN();

	public static class Generic {
		public boolean useEngInsteadOfChin = true;

		public String botToken = "";
		public String botPlayingStatus = "Minecraft";
		public String botListeningStatus = "";

		public String webhookUrl = "";

		public String channelId = "";
		public String consoleLogChannelId = "";

		public boolean useUuidInsteadOfName = true;

		public String avatarApi = "https://mc-heads.net/avatar/%player%.png";

		public boolean broadcastCommandExecution = true;

		public boolean modifyChatMessages = true;

		public boolean announceHighMspt = true;
		public int msptLimit = 50;

		public boolean mentionAdmins = true;

		public List<String> excludedCommands = List.of("/tell");

		public List<String> adminsIds = new ArrayList<>();

		// TODO Link players to Discord accounts
		// TODO Display message reply
	}

	public static class MultiServer {
		public boolean enable = true;
		public String host = "127.0.0.1";
		public int port = 5000;
		public String name = "SMP";
	}

	public static class TextsZH {
		public String serverStarted = "**服务器已启动！**";
		public String serverStopped = "**服务器已关闭！**";

		public String joinServer = "**%playerName% 加入了服务器**";
		public String leftServer = "**%playerName% 离开了服务器**";

		public String deathMessage = "**%deathMessage%**";

		public String advancementTask = "**%playerName% 达成了进度 [%advancement%]**";
		public String advancementChallenge = "**%playerName% 完成了挑战 [%advancement%]**";
		public String advancementGoal = "**%playerName% 达成了目标 [%advancement%]**";

		public String highMspt = "**服务器 MSPT (%mspt%) 高于 %msptLimit%！**";

		public String consoleLogMessage = "[%time%] [INFO] %message%";
	}

	public static class TextsEN {
		public String serverStarted = "**Server started!**";
		public String serverStopped = "**Server stopped!**";

		public String joinServer = "**%playerName% joined the game**";
		public String leftServer = "**%playerName% left the game**";

		public String deathMessage = "**%deathMessage%**";

		public String advancementTask = "**%playerName% has made the advancement [%advancement%]**";
		public String advancementChallenge = "**%playerName% has completed the challenge [%advancement%]**";
		public String advancementGoal = "**%playerName% has reached the goal [%advancement%]**";

		public String highMspt = "**Server MSPT (%mspt%) is above %msptLimit%!**";

		public String consoleLogMessage = "[%time%] [INFO] %message%";
	}
}

