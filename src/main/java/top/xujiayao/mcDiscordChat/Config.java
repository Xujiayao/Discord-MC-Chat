package top.xujiayao.mcDiscordChat;

import top.xujiayao.mcDiscordChat.objects.Player;
import top.xujiayao.mcDiscordChat.objects.Stats;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Xujiayao
 */
public class Config {

	// Sets if MCDiscordChat Should Modify In-Game Chat Messages
	public static boolean modifyChatMessages = true;

	// Bot Token; see https://discordpy.readthedocs.io/en/latest/discord.html
	public static String botToken = "NzkyNDIxOTQ3OTExODMxNTYy." + "X-decg." + "u7VRPDqSmOXHQm-_vkwDaVIqmEo";

	// Bot Game Status; What will be displayed on the bot's game status (leave empty for nothing)
	public static String botListeningStatus = "主人敲键盘的声音~";

	// Webhook URL; see
	// https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks
	public static String webhookURL = "https://discord.com/api/webhooks/793756425818079252/t-LPDAK_0R-C2aaPzgWSj3TmBKaL26Cete8hH6POGoX4ub2S6qjM85czRAch7n-ukehX";

	// Use UUID instead nickname to request player head on webhook
	public static boolean useUUIDInsteadNickname = true;

	// Admins ids in Discord; see
	// https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID
	// If more than one, enclose each id  like this: {"000", "111", "222"}
	public static String[] adminsIds = {"769470378073653269", "664857360602365990"};

	// Channel id in Discord
	public static String channelId = "792407823295184906";

	// If you enabled "Server Members Intent" in the bot's config page, change it to true.
	// (This is only necessary if you want to enable discord mentions inside the game)
	public static boolean membersIntents = true;

	// Should announce when a players join/leave the server?
	public static boolean announcePlayers = true;

	// Should announce when a players get an advancement?
	public static boolean announceAdvancements = true;

	// Should announce when a player die?
	public static boolean announceDeaths = true;

	// Banned Discord users' ID
	public static List<String> bannedDiscord = new ArrayList<>();

	// Banned Minecraft players' name
	public static List<String> bannedMinecraft = new ArrayList<>();

	// Name of the world folder
	public static String worldName = "world";

	// List of players { Player(String name, String uuid, String expiresOn) }
	public static List<Player> playerList;

	// List of files in Stats folder { file }
	public static List<File> statsFileList;

	// List of players stats { Player(String name, String uuid, String content) }
	public static List<Stats> statsList;

	// The scoreboard HashMap
	public static HashMap<String, Integer> scoreboardMap;

	// Minecraft -> Discord
	// Server started message
	public static String serverStarted = "**服务器已启动！**";

	// Minecraft -> Discord
	// Server stopped message
	public static String serverStopped = "**服务器已关闭！**";

	// Minecraft -> Discord
	// Join server
	// ---
	// Available placeholders:
	// %playername% | Player name
	public static String joinServer = "**%playername% 加入了服务器**";

	// Minecraft -> Discord
	// Left server
	// ---
	// Available placeholders:
	// %playername% | Player name
	public static String leftServer = "**%playername% 离开了服务器**";

	// Minecraft -> Discord
	// Death message
	// ---
	// Available placeholders:
	// %playername% | Player name
	// %deathmessage% | Death message
	public static String deathMessage = "**%deathmessage%**";

	// Minecraft -> Discord
	// Advancement type task message
	// ---
	// Available placeholders:
	// %playername% | Player name
	// %advancement% | Advancement name
	public static String advancementTask = "**%playername% 达成了进度 [%advancement%]**";

	// Minecraft -> Discord
	// Advancement type challenge message
	// ---
	// Available placeholders:
	// %playername% | Player name
	// %advancement% | Advancement name
	public static String advancementChallenge = "**%playername% 完成了挑战 [%advancement%]**";

	// Minecraft -> Discord
	// Advancement type goal message
	// ---
	// Available placeholders:
	// %playername% | Player name
	// %advancement% | Advancement name
	public static String advancementGoal = "**%playername% 达成了目标 [%advancement%]**";

	// Discord -> Minecraft
	// Colored part of the message
	// This part of the message will receive the same color as the role in the discord, comes before the colorless part
	// ---
	// Available placeholders:
	// %discordname% | User nickname in the discord server
	// %message% | The message
	public static String coloredText = "[Discord] ";

	// Discord -> Minecraft
	// Colorless (white) part of the message
	// I think you already know what it is by the other comment
	// ---
	// Available placeholders:
	// %discordname% | Nickname of the user in the discord server
	// %message% | The message
	public static String colorlessText = "<%discordname%> %message%";

	// Replaces the § symbol with & in any discord message to avoid formatted messages
	public static boolean removeVanillaFormattingFromDiscord = false;

	// Removes line break from any discord message to avoid spam
	public static boolean removeLineBreakFromDiscord = false;
}
