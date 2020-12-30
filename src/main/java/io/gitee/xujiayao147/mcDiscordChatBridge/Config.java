package io.gitee.xujiayao147.mcDiscordChatBridge;

/**
 * @author Xujiayao
 */
public class Config {
	// Sets if MC Discord Chat Bridge Should Modify In-Game Chat Messages
	public boolean modifyChatMessages = true;

	// Bot Token; see https://discordpy.readthedocs.io/en/latest/discord.html
	public String botToken = "NzkyNDIxOTQ3OTExODMxNTYy." + "X-decg." + "u7VRPDqSmOXHQm-_vkwDaVIqmEo";

	// Bot Game Status; What will be displayed on the bot's game status (leave empty
	// for nothing)
	public String botListeningStatus = "主人敲键盘的声音~";

	// Enable Webhook; If enabled, player messages will be send using a webhook with
	// the players name and head, instead of a regular message.
	public boolean isWebhookEnabled = true;

	// Webhook URL; see
	// https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks
	public String webhookURL = "https://discord.com/api/webhooks/793756425818079252/t-LPDAK_0R-C2aaPzgWSj3TmBKaL26Cete8hH6POGoX4ub2S6qjM85czRAch7n-ukehX";

	// Admins ids in Discord; see
	// https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID-\nIf
	// more than one, enclose each id in quotation marks separated by commas, like
	// this:\n\"adminsIds\": [ \n\t\t\"000\",\n\t\t\"111\",\n\t\t\"222\"\n\t]
	public String[] adminsIds = { "664857360602365990", "769470378073653269" };

	// Channel id in Discord
	public String channelId = "792407823295184906";

	// If you enabled \"Server Members Intent\" in the bot's config page, change it
	// to true. (This is only necessary if you want to enable discord mentions
	// inside the game)
	public boolean membersIntents = true;

	// Should announce when a players join/leave the server?
	public boolean announcePlayers = true;

	// Should announce when a players get an advancement?
	public boolean announceAdvancements = true;

	// Should announce when a player die?
	public boolean announceDeaths = true;

	public Texts texts = new Texts();

	public static class Texts {

		// Minecraft -> Discord\nPlayer chat message (Only used when Webhook is
		// disabled)\nAvailable placeholders:\n%playername% | Player
		// name\n%playermessage% | Player message
		public String playerMessage = "**%playername%:** %playermessage%";

		// Minecraft -> Discord\nServer started message
		public String serverStarted = "**服务器已启动！**";

		// Minecraft -> Discord\nServer stopped message
		public String serverStopped = "**服务器已关闭！**";

		// Minecraft -> Discord\nJoin server\nAvailable placeholders:\n%playername% |
		// Player name
		public String joinServer = "**%playername% 加入了游戏**";

		// Minecraft -> Discord\nLeft server\nAvailable placeholders:\n%playername% |
		// Player name
		public String leftServer = "**%playername% 离开了游戏**";

		// Minecraft -> Discord\nDeath message\nAvailable placeholders:\n%playername% |
		// Player name\n%deathmessage% | Death message
		public String deathMessage = "**%deathmessage%**";

		// Minecraft -> Discord\nAdvancement type task message\nAvailable
		// placeholders:\n%playername% | Player name\n%advancement% | Advancement name
		public String advancementTask = "**%playername% 达成了进度 [%advancement%]**";

		// Minecraft -> Discord\nAdvancement type challenge message\nAvailable
		// placeholders:\n%playername% | Player name\n%advancement% | Advancement name
		public String advancementChallenge = "**%playername% 完成了挑战 [%advancement%]**";

		// Minecraft -> Discord\nAdvancement type goal message\nAvailable
		// placeholders:\n%playername% | Player name\n%advancement% | Advancement name
		public String advancementGoal = "**%playername% 达成了目标 [%advancement%]**";

		// Discord -> Minecraft\nColored part of the message, this part of the message
		// will receive the same color as the role in the discord, comes before the
		// colorless part\nAvailable placeholders:\n%discordname% | User nickname in the
		// guild\n%message% | The message
		public String coloredText = "[Discord] ";

		// Discord -> Minecraft\nColorless (white) part of the message, I think you
		// already know what it is by the other comment\nAvailable
		// placeholders:\n%discordname% | Nickname of the user in the guild\n%message% |
		// The message
		public String colorlessText = "<%discordname%> %message%";
	}
}
