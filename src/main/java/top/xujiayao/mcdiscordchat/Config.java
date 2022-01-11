package top.xujiayao.mcdiscordchat;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Xujiayao
 */
public class Config {

	public Generic generic = new Generic();
	public MultiServer multiServer = new MultiServer();
	public TextsZH textsZH = new TextsZH();
	public TextsEN textsEN = new TextsEN();

	public static class Generic {
		// [Required] Language MCDiscordChat uses
		// (Chinese if true, English if false)
		public boolean switchLanguageFromChinToEng = true;

		// [Required] Discord bot token
		public String botToken = "";

		// [Optional] Discord bot activity status
		// (null when empty)
		public String botListeningStatus = "";

		// [Required] Webhook URL
		public String webhookURL = "";

		// [Required] Discord Channel ID
		// (Right click the channel to copy the ID, you have to turn on developer mode in Discord settings)
		public String channelId = "";

		// [Required] Server world name
		public String worldName = "world";

		// [Required] URL of the Avatar API for Webhook
		// (Example: MCHeads https://mc-heads.net/avatar/%player% / Visage https://visage.surgeplay.com/bust/%player%)
		// Contributed by FireNH
		public String avatarAPI = "https://mc-heads.net/avatar/%player%";

		// [Required] Modify in-game chat messages
		// (not enable or disable MCDiscordChat)
		public boolean modifyChatMessages = true;

		// [Required] Broadcast player command execution
		public boolean broadcastCommandExecution = true;

		// [Required] Use more than two MCDiscordChat in one Discord channel
		// (name of the bot must be in the following format: [%serverDisplayName%] %botName%)
		public boolean multiServer = false;

		// [Required] Use UUID instead nickname to request player head on Webhook
		public boolean useUUIDInsteadNickname = true;

		// [Required] Enable in-game mentions (@) Discord users
		public boolean membersIntents = true;

		// [Required] Announce when a player join / leave the server
		public boolean announcePlayers = true;

		// [Required] Announce when a player reached a progress / achieved a goal / completed a challenge
		public boolean announceAdvancements = true;

		// [Required] Announce when a player die
		public boolean announceDeaths = true;

		// [Required] Announce when Server MSPT is above MSPT Limit
		public boolean announceHighMSPT = true;

		// [Required] Server MSPT Limit
		public int msptLimit = 50;

		// [Required] Replaces the § symbol with & in any discord message to avoid formatted messages
		public boolean removeVanillaFormattingFromDiscord = false;

		// [Required] Removes line break from any discord message to avoid spam
		public boolean removeLineBreakFromDiscord = false;

		// [Required] MCDiscordChat Super Admin ID List, has permission to add and remove admins, and have all permissions admins have
		// (can have more than one)
		public List<String> superAdminsIds = new ArrayList<>();

		// [Optional] MCDiscordChat Admin ID List, has permission to modify blacklist, etc.
		// (can have more than one)
		public List<String> adminsIds = new ArrayList<>();

		// [Optional] MCDiscordChat Discord ID Blacklist, disallow processing of messages from a Discord user
		// (can have more than one)
		public List<String> bannedDiscord = new ArrayList<>();

		// [Optional] MCDiscordChat Player Name Blacklist, disallow processing of messages from a Minecraft player
		// (can have more than one)
		public List<String> bannedMinecraft = new ArrayList<>();
	}

	public static class MultiServer {
		// [Required] Server display name
		public String serverDisplayName = "SMP";

		// [Required] Discord bot name
		// (Example: When the name of the bot is '[SMP] MCDC Bot', set it to 'MCDC Bot')
		public String botName = "MCDC Bot";
	}

	public static class TextsZH {
		// Placeholders         Description
		// ------------------------------
		// %playername%         Name of a Minecraft player
		// %deathmessage%       Death message
		// %advancement%        Progress / goal / challenge name
		// %servername%         'Discord' (becomes server name when using multi-server mode)
		// %name%               Nickname of a user in Discord (becomes player name when using multi-server mode)
		// %message%	      Content of message
		// %mspt%               Server MSPT
		// %msptLimit%          Server MSPT Limit
		// %mentionAllAdmins%	String used to mention all MCDiscordChat admins

		public String serverStarted = "**服务器已启动！**";
		public String serverStopped = "**服务器已关闭！**";

		public String joinServer = "**%playername% 加入了服务器**";
		public String leftServer = "**%playername% 离开了服务器**";

		public String deathMessage = "**%deathmessage%**";

		public String advancementTask = "**%playername% 达成了进度 [%advancement%]**";
		public String advancementChallenge = "**%playername% 完成了挑战 [%advancement%]**";
		public String advancementGoal = "**%playername% 达成了目标 [%advancement%]**";

		public String highMSPT = "**%mentionAllAdmins% 服务器 MSPT (%mspt%) 高于 %msptLimit%！**";

		public String blueColoredText = "[%servername%] ";
		public String roleColoredText = "<%name%>";
		public String colorlessText = " %message%";
	}


	public static class TextsEN {
		// Placeholders         Description
		// ------------------------------
		// %playername%         Name of a Minecraft player
		// %deathmessage%       Death message
		// %advancement%        Progress / goal / challenge name
		// %servername%         'Discord' (becomes server name when using multi-server mode)
		// %name%               Nickname of a user in Discord (becomes player name when using multi-server mode)
		// %message%	      Content of message
		// %mspt%               Server MSPT
		// %msptLimit%          Server MSPT Limit
		// %mentionAllAdmins%	String used to mention all MCDiscordChat admins

		public String serverStarted = "**Server started!**";
		public String serverStopped = "**Server stopped!**";

		public String joinServer = "**%playername% joined the game**";
		public String leftServer = "**%playername% left the game**";

		public String deathMessage = "**%deathmessage%**";

		public String advancementTask = "**%playername% has made the advancement [%advancement%]**";
		public String advancementChallenge = "**%playername% has completed the challenge [%advancement%]**";
		public String advancementGoal = "**%playername% has reached the goal [%advancement%]**";

		public String highMSPT = "**%mentionAllAdmins% Server MSPT (%mspt%) is above %msptLimit%!**";

		public String blueColoredText = "[%servername%] ";
		public String roleColoredText = "<%name%>";
		public String colorlessText = " %message%";
	}
}
