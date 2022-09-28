package top.xujiayao.mcdiscordchat;

import java.util.ArrayList;
import java.util.List;

import static top.xujiayao.mcdiscordchat.Main.VERSION;

/**
 * @author Xujiayao
 */
public class Config {

	// More information + Docs: https://blog.xujiayao.top/posts/4ba0a17a/

	public Generic generic = new Generic();
	public MultiServer multiServer = new MultiServer();
	public CustomMessage customMessage = new CustomMessage();

	public String latestVersion = VERSION;
	public long latestCheckTime = System.currentTimeMillis() - 300000000;

	public static class Generic {
		public String language = "en_us";

		public String botToken = "";
		public String botPlayingStatus = "Minecraft";
		public String botListeningStatus = "";

		public String webhookUrl = "";

		public String channelId = "";
		public String consoleLogChannelId = "";
		public String updateNotificationChannelId = "";

		public boolean useUuidInsteadOfName = true;

		public String avatarApi = "https://mc-heads.net/avatar/%player%.png";

		public boolean broadcastCommandExecution = true;

		public boolean announceServerStartStop = true;
		public boolean announcePlayerJoinLeave = true;
		public boolean announceDeathMessages = true;
		public boolean announceAdvancements = true;

		public boolean allowMentions = true;
		public boolean formatChatMessages = true;

		public boolean useServerNickname = true;

		public boolean announceHighMspt = true;
		public int msptCheckInterval = 5000;
		public int msptLimit = 50;

		@SuppressWarnings("unused")
		public boolean notifyUpdates = true;
		public boolean mentionAdminsForUpdates = true;

		public boolean updateChannelTopic = true;
		public int channelTopicUpdateInterval = 600000;

		public int discordNewlineLimit = 3;

		public List<String> excludedCommands = List.of("/msg", "/tell", "/tellraw", "/w");

		public List<String> adminsIds = new ArrayList<>();
	}

	public static class MultiServer {
		public boolean enable = false;
		public String host = "127.0.0.1";
		public int port = 5000;
		public String name = "SMP";
		public List<String> botIds = new ArrayList<>();
	}

	@SuppressWarnings("unused")
	public static class CustomMessage {
		public String unformattedResponseMessage = "";
		public String unformattedChatMessage = "";
		public String unformattedOtherMessage = "";
		public String unformattedCommandNotice = "";

		public String formattedResponseMessage = "";
		public String formattedChatMessage = "";
		public String formattedOtherMessage = "";
		public String formattedCommandNotice = "";

		public String serverStarted = "";
		public String serverStopped = "";

		public String joinServer = "";
		public String leftServer = "";

		public String deathMessage = "";

		public String advancementTask = "";
		public String advancementChallenge = "";
		public String advancementGoal = "";

		public String highMspt = "";

		public String offlineChannelTopic = "";
		public String onlineChannelTopic = "";
		public String onlineChannelTopicForMultiServer = "";
	}
}

