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

		// Language MCDiscordChat uses
		// false: Chinese
		// true: English
		public boolean switchLanguageFromChinToEng = true;

		// Bot Token; see https://discordpy.readthedocs.io/en/latest/discord.html
		public String botToken = "";

		// Bot Game Status; What will be displayed on the bot's game status (leave empty for nothing)
		public String botListeningStatus = "";

		// Webhook URL; see
		// https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks
		public String webhookURL = "";

		// Channel id in Discord
		public String channelId = "";

		// Name of the world folder
		public String worldName = "world";

		/*  URL of the skin API to use. Include "%PLAYER%" where to put either the player UUID or username. 
		 *	Keep in mind that not all skin APIs work with UUIDs or usernames, so know what you're doing first.
		 *	By default this uses visage.surgeplay.com, but the original author Xujiayao used mc-heads.net.
		 *  If you want to use mc-heads.net, change this value to "https://mc-heads.net/avatar/%PLAYER%"
		 * 	If you want to use visage, change this value to "https://visage.surgeplay.com/bust/%PLAYER%"
		 * 
		 * 	Contributed by FireNH
		 */
		public String skinAPI = "https://mc-heads.net/avatar/%PLAYER%";
		
		// The regex of the string to replace in the skinAPI.
		public String regexTargetSkinAPI = "(%PLAYER%)";

		// Set if MCDiscordChat should modify in-game chat messages
		public boolean modifyChatMessages = true;

		// Set if MCDiscordChat should broadcast player command execution
		public boolean broadcastCommandExecution = true;

		// Set if using more than two MCDiscordChat in one Discord server
		// Name of the bot must be in the following format:
		// [%serverDisplayName%] %botName%
		public boolean multiServer = false;

		// Use UUID instead nickname to request player head on webhook
		public boolean useUUIDInsteadNickname = true;

		// If you enabled "Server Members Intent" in the bot's config page, change it to true.
		// (This is only necessary if you want to enable discord mentions inside the game)
		public boolean membersIntents = true;

		// Should announce when a players join/leave the server?
		public boolean announcePlayers = true;

		// Should announce when a players get an advancement?
		public boolean announceAdvancements = true;

		// Should announce when a player die?
		public boolean announceDeaths = true;

		// Should announce when MSPT is above MSPT Limit?
		public boolean announceHighMSPT = true;

		// MSPT Limit for announceHighMSPT
		public int msptLimit = 50;

		// Super Admins ids in Discord; see
		// https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID
		// If more than one, enclose each id like this: ["000", "111", "222"]
		public List<String> superAdminsIds = new ArrayList<>();

		// Admins ids in Discord; see
		// https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID
		// If more than one, enclose each id like this: ["000", "111", "222"]
		public List<String> adminsIds = new ArrayList<>();

		// Banned Discord users' id
		public List<String> bannedDiscord = new ArrayList<>();

		// Banned Minecraft players' name
		public List<String> bannedMinecraft = new ArrayList<>();
	}

	public static class MultiServer {
		// Server display name
		// e.g. 'SMP'
		public String serverDisplayName = "";

		// MCDiscordChat Bot name
		// e.g. if name of the bot is '[SMP] MCDC Bot', set it to 'MCDC Bot'
		public String botName = "MCDC Bot";
	}

	public static class TextsZH {

		// Minecraft -> Discord
		// Server started message
		public String serverStarted = "**服务器已启动！**";

		// Minecraft -> Discord
		// Server stopped message
		public String serverStopped = "**服务器已关闭！**";

		// Minecraft -> Discord
		// Join server
		// ---
		// Available placeholders:
		// %playername% | Player name
		public String joinServer = "**%playername% 加入了服务器**";

		// Minecraft -> Discord
		// Left server
		// ---
		// Available placeholders:
		// %playername% | Player name
		public String leftServer = "**%playername% 离开了服务器**";

		// Minecraft -> Discord
		// Death message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %deathmessage% | Death message
		public String deathMessage = "**%deathmessage%**";

		// Minecraft -> Discord
		// Advancement type task message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		public String advancementTask = "**%playername% 达成了进度 [%advancement%]**";

		// Minecraft -> Discord
		// Advancement type challenge message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		public String advancementChallenge = "**%playername% 完成了挑战 [%advancement%]**";

		// Minecraft -> Discord
		// Advancement type goal message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		public String advancementGoal = "**%playername% 达成了目标 [%advancement%]**";

		// Minecraft -> Discord
		// High MSPT message
		// ---
		// Available placeholders:
		// %mspt% | Server MSPT
		// %msptLimit% | MSPT Limit
		// %mentionAllAdmins% | Mention all MCDiscordChat admins
		public String highMSPT = "**%mentionAllAdmins% 服务器 MSPT (%mspt%) 高于 %msptLimit%！**";

		// Discord -> Minecraft
		// Blue colored part of the message
		// This part of the message comes before the role colored and colorless part
		// ---
		// Available placeholders:
		// %servername% | 'Discord' or 'server name when using multi-server'
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		public String blueColoredText = "[%servername%] ";

		// Discord -> Minecraft
		// Role colored part of the message
		// This part of the message will receive the same color as the role in the discord, comes before the colorless part
		// ---
		// Available placeholders:
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		public String roleColoredText = "<%name%>";

		// Discord -> Minecraft
		// Colorless (white) part of the message
		// I think you already know what it is by the other comment
		// ---
		// Available placeholders:
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		public String colorlessText = " %message%";

		// Replaces the § symbol with & in any discord message to avoid formatted messages
		public boolean removeVanillaFormattingFromDiscord = false;

		// Removes line break from any discord message to avoid spam
		public boolean removeLineBreakFromDiscord = false;
	}


	public static class TextsEN {

		// Minecraft -> Discord
		// Server started message
		public String serverStarted = "**Server started!**";

		// Minecraft -> Discord
		// Server stopped message
		public String serverStopped = "**Server stopped!**";

		// Minecraft -> Discord
		// Join server
		// ---
		// Available placeholders:
		// %playername% | Player name
		public String joinServer = "**%playername% joined the game**";

		// Minecraft -> Discord
		// Left server
		// ---
		// Available placeholders:
		// %playername% | Player name
		public String leftServer = "**%playername% left the game**";

		// Minecraft -> Discord
		// Death message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %deathmessage% | Death message
		public String deathMessage = "**%deathmessage%**";

		// Minecraft -> Discord
		// Advancement type task message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		public String advancementTask = "**%playername% has made the advancement [%advancement%]**";

		// Minecraft -> Discord
		// Advancement type challenge message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		public String advancementChallenge = "**%playername% has completed the challenge [%advancement%]**";

		// Minecraft -> Discord
		// Advancement type goal message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		public String advancementGoal = "**%playername% has reached the goal [%advancement%]**";

		// Minecraft -> Discord
		// High MSPT message
		// ---
		// Available placeholders:
		// %mspt% | Server MSPT
		// %msptLimit% | MSPT Limit
		// %mentionAllAdmins% | Mention all MCDiscordChat admins
		public String highMSPT = "**%mentionAllAdmins% Server MSPT (%mspt%) is above %msptLimit%!**";

		// Discord -> Minecraft
		// Blue colored part of the message
		// This part of the message comes before the role colored and colorless part
		// ---
		// Available placeholders:
		// %servername% | 'Discord' or 'server name when using multi-server'
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		public String blueColoredText = "[%servername%] ";

		// Discord -> Minecraft
		// Role colored part of the message
		// This part of the message will receive the same color as the role in the discord, comes before the colorless part
		// ---
		// Available placeholders:
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		public String roleColoredText = "<%name%>";

		// Discord -> Minecraft
		// Colorless (gray) part of the message
		// I think you already know what it is by the other comment
		// ---
		// Available placeholders:
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		public String colorlessText = " %message%";

		// Replaces the § symbol with & in any discord message to avoid formatted messages
		public boolean removeVanillaFormattingFromDiscord = false;

		// Removes line break from any discord message to avoid spam
		public boolean removeLineBreakFromDiscord = false;
	}
}
