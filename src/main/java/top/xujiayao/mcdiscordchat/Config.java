package top.xujiayao.mcdiscordchat;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Xujiayao
 */
public class Config {

	@Expose
	public Generic generic = new Generic();

	@Expose
	public MultiServer multiServer = new MultiServer();

	@Expose
	public TextsZH textsZH = new TextsZH();

	@Expose
	public TextsEN textsEN = new TextsEN();

	public static class Generic {

		// Language MCDiscordChat uses
		// false: Chinese
		// true: English
		@Expose
		public boolean switchLanguageFromChinToEng = true;

		// Bot Token; see https://discordpy.readthedocs.io/en/latest/discord.html
		@Expose
		public String botToken = "";

		// Bot Game Status; What will be displayed on the bot's game status (leave empty for nothing)
		@Expose
		public String botListeningStatus = "";

		// Webhook URL; see
		// https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks
		@Expose
		public String webhookURL = "";

		// Channel id in Discord
		@Expose
		public String channelId = "";

		// Name of the world folder
		@Expose
		public String worldName = "world";

		// Set if MCDiscordChat should modify in-game chat messages
		@Expose
		public boolean modifyChatMessages = true;

		// Set if MCDiscordChat should broadcast player command execution
		@Expose
		public boolean broadcastCommandExecution = true;

		// Set if using more than two MCDiscordChat in one Discord server
		// Name of the bot must be in the following format:
		// [%serverDisplayName%] %botName%
		@Expose
		public boolean multiServer = false;

		// Use UUID instead nickname to request player head on webhook
		@Expose
		public boolean useUUIDInsteadNickname = true;

		// If you enabled "Server Members Intent" in the bot's config page, change it to true.
		// (This is only necessary if you want to enable discord mentions inside the game)
		@Expose
		public boolean membersIntents = true;

		// Should announce when a players join/leave the server?
		@Expose
		public boolean announcePlayers = true;

		// Should announce when a players get an advancement?
		@Expose
		public boolean announceAdvancements = true;

		// Should announce when a player die?
		@Expose
		public boolean announceDeaths = true;

		// Should announce when MSPT is above MSPT Limit?
		@Expose
		public boolean announceHighMSPT = true;

		// MSPT Limit for announceHighMSPT
		@Expose
		public int msptLimit = 50;

		// Super Admins ids in Discord; see
		// https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID
		// If more than one, enclose each id like this: {"000", "111", "222"}
		@Expose
		public List<String> superAdminsIds = new ArrayList<>();

		// Admins ids in Discord; see
		// https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID
		// If more than one, enclose each id like this: {"000", "111", "222"}
		@Expose
		public List<String> adminsIds = new ArrayList<>();

		// Banned Discord users' id
		@Expose
		public List<String> bannedDiscord = new ArrayList<>();

		// Banned Minecraft players' name
		@Expose
		public List<String> bannedMinecraft = new ArrayList<>();
	}

	public static class MultiServer {
		// Server display name
		// e.g. 'SMP'
		@Expose
		public String serverDisplayName = "";

		// MCDiscordChat Bot name
		// e.g. if name of the bot is '[SMP] MCDC Bot', set it to 'MCDC Bot'
		@Expose
		public String botName = "MCDC Bot";
	}

	public static class TextsZH {

		// Minecraft -> Discord
		// Server started message
		@Expose
		public String serverStarted = "**服务器已启动！**";

		// Minecraft -> Discord
		// Server stopped message
		@Expose
		public String serverStopped = "**服务器已关闭！**";

		// Minecraft -> Discord
		// Join server
		// ---
		// Available placeholders:
		// %playername% | Player name
		@Expose
		public String joinServer = "**%playername% 加入了服务器**";

		// Minecraft -> Discord
		// Left server
		// ---
		// Available placeholders:
		// %playername% | Player name
		@Expose
		public String leftServer = "**%playername% 离开了服务器**";

		// Minecraft -> Discord
		// Death message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %deathmessage% | Death message
		@Expose
		public String deathMessage = "**%deathmessage%**";

		// Minecraft -> Discord
		// Advancement type task message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		@Expose
		public String advancementTask = "**%playername% 达成了进度 [%advancement%]**";

		// Minecraft -> Discord
		// Advancement type challenge message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		@Expose
		public String advancementChallenge = "**%playername% 完成了挑战 [%advancement%]**";

		// Minecraft -> Discord
		// Advancement type goal message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		@Expose
		public String advancementGoal = "**%playername% 达成了目标 [%advancement%]**";

		// Minecraft -> Discord
		// High MSPT message
		// ---
		// Available placeholders:
		// %mspt% | Server MSPT
		// %msptLimit% | MSPT Limit
		// %mentionAllAdmins% | Mention all MCDiscordChat admins
		@Expose
		public String highMSPT = "**%mentionAllAdmins% 服务器 MSPT (%mspt%) 高于 %msptLimit%！**";

		// Discord -> Minecraft
		// Blue colored part of the message
		// This part of the message comes before the role colored and colorless part
		// ---
		// Available placeholders:
		// %servername% | 'Discord' or 'server name when using multi-server'
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		@Expose
		public String blueColoredText = "[%servername%] ";

		// Discord -> Minecraft
		// Role colored part of the message
		// This part of the message will receive the same color as the role in the discord, comes before the colorless part
		// ---
		// Available placeholders:
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		@Expose
		public String roleColoredText = "<%name%>";

		// Discord -> Minecraft
		// Colorless (white) part of the message
		// I think you already know what it is by the other comment
		// ---
		// Available placeholders:
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		@Expose
		public String colorlessText = " %message%";

		// Replaces the § symbol with & in any discord message to avoid formatted messages
		@Expose
		public boolean removeVanillaFormattingFromDiscord = false;

		// Removes line break from any discord message to avoid spam
		@Expose
		public boolean removeLineBreakFromDiscord = false;
	}


	public static class TextsEN {

		// Minecraft -> Discord
		// Server started message
		@Expose
		public String serverStarted = "**Server started!**";

		// Minecraft -> Discord
		// Server stopped message
		@Expose
		public String serverStopped = "**Server stopped!**";

		// Minecraft -> Discord
		// Join server
		// ---
		// Available placeholders:
		// %playername% | Player name
		@Expose
		public String joinServer = "**%playername% joined the game**";

		// Minecraft -> Discord
		// Left server
		// ---
		// Available placeholders:
		// %playername% | Player name
		@Expose
		public String leftServer = "**%playername% left the game**";

		// Minecraft -> Discord
		// Death message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %deathmessage% | Death message
		@Expose
		public String deathMessage = "**%deathmessage%**";

		// Minecraft -> Discord
		// Advancement type task message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		@Expose
		public String advancementTask = "**%playername% has made the advancement [%advancement%]**";

		// Minecraft -> Discord
		// Advancement type challenge message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		@Expose
		public String advancementChallenge = "**%playername% has completed the challenge [%advancement%]**";

		// Minecraft -> Discord
		// Advancement type goal message
		// ---
		// Available placeholders:
		// %playername% | Player name
		// %advancement% | Advancement name
		@Expose
		public String advancementGoal = "**%playername% has reached the goal [%advancement%]**";

		// Minecraft -> Discord
		// High MSPT message
		// ---
		// Available placeholders:
		// %mspt% | Server MSPT
		// %msptLimit% | MSPT Limit
		// %mentionAllAdmins% | Mention all MCDiscordChat admins
		@Expose
		public String highMSPT = "**%mentionAllAdmins% Server MSPT (%mspt%) is above %msptLimit%!**";

		// Discord -> Minecraft
		// Blue colored part of the message
		// This part of the message comes before the role colored and colorless part
		// ---
		// Available placeholders:
		// %servername% | 'Discord' or 'server name when using multi-server'
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		@Expose
		public String blueColoredText = "[%servername%] ";

		// Discord -> Minecraft
		// Role colored part of the message
		// This part of the message will receive the same color as the role in the discord, comes before the colorless part
		// ---
		// Available placeholders:
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		@Expose
		public String roleColoredText = "<%name%>";

		// Discord -> Minecraft
		// Colorless (gray) part of the message
		// I think you already know what it is by the other comment
		// ---
		// Available placeholders:
		// %name% | Nickname of the user in the discord server (becomes player name when using multi-server)
		// %message% | The message
		@Expose
		public String colorlessText = " %message%";

		// Replaces the § symbol with & in any discord message to avoid formatted messages
		@Expose
		public boolean removeVanillaFormattingFromDiscord = false;

		// Removes line break from any discord message to avoid spam
		@Expose
		public boolean removeLineBreakFromDiscord = false;
	}
}
