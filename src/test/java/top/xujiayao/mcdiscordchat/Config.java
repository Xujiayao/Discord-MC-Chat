package top.xujiayao.mcdiscordchat;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Xujiayao
 */
public class Config {

	public Generic generic = new Generic();
	public MultiServer multiServer = new MultiServer();

	public static class Generic {
		// [Required] Server world name
		public String worldName = "world";

		// [Required] Use more than two MCDiscordChat in one Discord channel
		// (name of the bot must be in the following format: [%serverDisplayName%] %botName%)
		public boolean multiServer = false;

		// [Required] Use UUID instead nickname to request player head on Webhook
		public boolean useUUIDInsteadNickname = true;

		// [Required] Announce when Server MSPT is above MSPT Limit
		public boolean announceHighMSPT = true;

		// [Required] Server MSPT Limit
		public int msptLimit = 50;

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
		// (example: when the name of the bot is '[SMP] MCDC Bot', set it to 'MCDC Bot')
		public String botName = "MCDC Bot";
	}
}
