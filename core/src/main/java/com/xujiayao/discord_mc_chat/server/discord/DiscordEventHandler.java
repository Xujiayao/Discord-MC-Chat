package com.xujiayao.discord_mc_chat.server.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.impl.StatsCommand;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.packets.events.DiscordEventPacket;
import com.xujiayao.discord_mc_chat.server.linking.LinkedAccountManager;
import com.xujiayao.discord_mc_chat.utils.LogFileUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.config.ModeManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles Discord JDA events.
 *
 * @author Xujiayao
 */
public class DiscordEventHandler extends ListenerAdapter {

	private static final int AUTOCOMPLETE_TIMEOUT_SECONDS = 5;

	/**
	 * Resolves the OP Level credential for a Discord user based on config mappings.
	 *
	 * @param member The Discord Member object (null if in DMs).
	 * @param user   The Discord User object.
	 * @return The resolved OP level (-1 to 4).
	 */
	private int getOpLevel(Member member, User user) {
		return OpLevelResolver.resolve(member, user);
	}

	/**
	 * Resolves the OP Level credential for a specific target server.
	 *
	 * @param member     The Discord Member object (null if in DMs).
	 * @param user       The Discord User object.
	 * @param serverName The target DMCC client server name.
	 * @return The resolved OP level (-1 to 4).
	 */
	private int getOpLevelForServer(Member member, User user, String serverName) {
		return OpLevelResolver.resolveForServer(member, user, serverName);
	}

	@Override
	public void onReady(@NotNull ReadyEvent event) {
		DiscordManager.updateBotPresence();
	}

	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
		event.deferReply().queue();

		int opLevel = getOpLevel(event.getMember(), event.getUser());
		String name = event.getName();

		switch (name) {
			case "execute" -> {
				String at = event.getOption("at", OptionMapping::getAsString);
				String command = event.getOption("command", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event, opLevel), name, at, command);
				broadcastDiscordCommandToMinecraft(event.getMember(), event.getUser(), "/" + name + " " + command);
			}
			case "console" -> {
				String at = event.getOption("at", OptionMapping::getAsString);
				String command = event.getOption("command", OptionMapping::getAsString);
				if (at != null) {
					// standalone mode: /console <at> <command>
					CommandManager.execute(new JdaCommandSender(event, opLevel), name, at, command);
				} else {
					// single_server mode: /console <command>
					CommandManager.execute(new JdaCommandSender(event, opLevel), name, command);
				}
				broadcastDiscordCommandToMinecraft(event.getMember(), event.getUser(), "/" + name + " " + command);
			}
			case "log" -> {
				String file = event.getOption("file", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event, opLevel), name, file);
			}
			case "stats" -> {
				String type = event.getOption("type", OptionMapping::getAsString);
				String stat = event.getOption("stat", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event, opLevel), name, type, stat);
			}
			case "link" -> {
				String code = event.getOption("code", OptionMapping::getAsString);
				CommandManager.execute(new JdaCommandSender(event, opLevel), name, code);
			}
			default -> CommandManager.execute(new JdaCommandSender(event, opLevel), name);
		}
	}

	@Override
	public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
		String commandName = event.getName();
		String focusedOption = event.getFocusedOption().getName();
		String currentValue = event.getFocusedOption().getValue();

		int opLevel = getOpLevel(event.getMember(), event.getUser());
		if (opLevel < ConfigManager.getInt("command_permission_levels." + commandName, 4)) {
			event.replyChoices(List.of()).queue();
			return;
		}

		List<Command.Choice> choices = new ArrayList<>();

		switch (commandName) {
			case "execute" -> {
				if ("at".equals(focusedOption)) {
					choices = getTargetAtChoices(currentValue);
				} else if ("command".equals(focusedOption)) {
					choices = getExecuteCommandChoices(currentValue, event);
				}
			}
			case "console" -> {
				if ("at".equals(focusedOption)) {
					choices = getTargetAtChoices(currentValue);
				} else if ("command".equals(focusedOption)) {
					choices = getConsoleCommandChoices(currentValue, event);
				}
			}
			case "log" -> {
				if ("file".equals(focusedOption)) {
					choices = getLogFileChoices(currentValue);
				}
			}
			case "stats" -> {
				if ("type".equals(focusedOption)) {
					choices = getStatsTypeChoices(currentValue);
				} else if ("stat".equals(focusedOption)) {
					String type = event.getOption("type", OptionMapping::getAsString);
					choices = getStatsStatChoices(type, currentValue);
				}
			}
		}

		event.replyChoices(choices).queue();
	}

	/**
	 * Gets auto-complete choices for the 'at' parameter (shared by execute and console).
	 * Includes "all_online_clients" as the first option, followed by configured server names.
	 *
	 * @param currentValue The current user input for filtering
	 * @return List of choices
	 */
	private List<Command.Choice> getTargetAtChoices(String currentValue) {
		List<Command.Choice> choices = new ArrayList<>();
		String lowerValue = currentValue.toLowerCase();

		// Add "all_online_clients" as the first option
		if ("all_online_clients".contains(lowerValue)) {
			choices.add(new Command.Choice("all_online_clients", "all_online_clients"));
		}

		// Add configured server names (only those online)
		List<String> serverNames = NetworkManager.getConnectedClientNames();
		for (String name : serverNames) {
			if (name.toLowerCase().contains(lowerValue)) {
				choices.add(new Command.Choice(name, name));
			}
		}

		// Discord limits to 25 choices
		return choices.stream().limit(25).collect(Collectors.toList());
	}

	/**
	 * Gets auto-complete choices for the 'command' parameter of the execute command.
	 * Sends a real-time auto-complete request to connected clients with the current input and OP level,
	 * so clients can provide DMCC command suggestions that the user is authorized to execute.
	 * <p>
	 * When a target server is selected, uses the per-server OP level for accurate suggestions.
	 *
	 * @param currentValue The current user input for filtering
	 * @param event        The auto-complete event to read other options
	 * @return List of choices
	 */
	private List<Command.Choice> getExecuteCommandChoices(String currentValue, CommandAutoCompleteInteractionEvent event) {
		String target = event.getOption("at", OptionMapping::getAsString);
		int opLevel;
		if (target != null && !target.isBlank() && !"all_online_clients".equalsIgnoreCase(target)) {
			opLevel = getOpLevelForServer(event.getMember(), event.getUser(), target);
		} else {
			opLevel = getOpLevel(event.getMember(), event.getUser());
		}

		if (currentValue.startsWith("/")) {
			currentValue = currentValue.substring(1);
		}

		Map<String, List<String>> autoCompleteLists = NetworkManager.requestExecuteAutoCompleteSnapshot(currentValue, opLevel, AUTOCOMPLETE_TIMEOUT_SECONDS);

		return autoCompleteLists.values().stream()
				.flatMap(List::stream)
				.distinct()
				.limit(25)
				.map(s -> new Command.Choice(s, s))
				.collect(Collectors.toList());
	}

	/**
	 * Gets auto-complete choices for the 'command' parameter of the console command.
	 * Sends a real-time auto-complete request to connected clients with the current input and OP level,
	 * so clients can provide Minecraft command suggestions via their Brigadier dispatcher.
	 * <p>
	 * When a target server is selected, uses the per-server OP level for accurate suggestions.
	 *
	 * @param currentValue The current user input for filtering
	 * @param event        The auto-complete event to read other options
	 * @return List of choices
	 */
	private List<Command.Choice> getConsoleCommandChoices(String currentValue, CommandAutoCompleteInteractionEvent event) {
		String target = event.getOption("at", OptionMapping::getAsString);
		int opLevel;
		if (target != null && !target.isBlank() && !"all_online_clients".equalsIgnoreCase(target)) {
			opLevel = getOpLevelForServer(event.getMember(), event.getUser(), target);
		} else {
			opLevel = getOpLevel(event.getMember(), event.getUser());
		}

		if (currentValue.startsWith("/")) {
			currentValue = currentValue.substring(1);
		}

		Map<String, List<String>> autoCompleteLists = NetworkManager.requestConsoleAutoCompleteSnapshot(currentValue, opLevel, AUTOCOMPLETE_TIMEOUT_SECONDS);

		return autoCompleteLists.values().stream()
				.flatMap(List::stream)
				.distinct()
				.limit(25)
				.map(s -> new Command.Choice(s, s))
				.collect(Collectors.toList());
	}

	/**
	 * Gets auto-complete choices for the 'file' parameter of the log command.
	 * In standalone mode, lists DMCC log files locally.
	 * Otherwise, lists Minecraft log files.
	 *
	 * @param currentValue The current user input for filtering
	 * @return List of choices
	 */
	private List<Command.Choice> getLogFileChoices(String currentValue) {
		List<String> logFiles = LogFileUtils.listLogFiles();
		String lowerValue = currentValue.toLowerCase();

		return logFiles.stream()
				.filter(f -> f.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(f -> new Command.Choice(f, f))
				.collect(Collectors.toList());
	}

	/**
	 * Gets auto-complete choices for the 'type' parameter of the stats command.
	 *
	 * @param currentValue The current user input for filtering
	 * @return List of choices
	 */
	private List<Command.Choice> getStatsTypeChoices(String currentValue) {
		StatsCommand.StatsProvider provider = StatsCommand.getProvider();
		if (provider == null) return List.of();

		String lowerValue = currentValue.toLowerCase();
		return provider.getStatTypes().stream()
				.filter(t -> t.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(t -> new Command.Choice(t, t))
				.collect(Collectors.toList());
	}

	/**
	 * Gets auto-complete choices for the 'stat' parameter of the stats command.
	 *
	 * @param type         The selected stat type
	 * @param currentValue The current user input for filtering
	 * @return List of choices
	 */
	private List<Command.Choice> getStatsStatChoices(String type, String currentValue) {
		StatsCommand.StatsProvider provider = StatsCommand.getProvider();
		if (provider == null || type == null || type.isBlank()) return List.of();

		String lowerValue = currentValue.toLowerCase();
		return provider.getStatNames(type).stream()
				.filter(s -> s.toLowerCase().contains(lowerValue))
				.limit(25)
				.map(s -> new Command.Choice(s, s))
				.collect(Collectors.toList());
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		if (event.getAuthor().isBot() || event.isWebhookMessage()) {
			return;
		}

		if (!Boolean.TRUE.equals(ConfigManager.getBoolean("broadcasts.discord_to_minecraft.chat"))) {
			return;
		}

		// Determine the monitored channel(s) for Discord→MC forwarding
		String chatChannelId = ConfigManager.getString("broadcasts.minecraft_to_discord.player.chat");
		if (chatChannelId == null || chatChannelId.isBlank()) {
			return;
		}

		// Check if the message is from the monitored channel
		TextChannel channel = event.getChannel().asTextChannel();
		if (!matchesChannel(channel, chatChannelId)) {
			return;
		}

		try {
			Message message = event.getMessage();
			Member member = event.getMember();
			User author = event.getAuthor();

			String effectiveName = member != null ? member.getEffectiveName() : author.getName();
			String roleColor = getRoleColorString(member);

			// Parse the message content
			String parsedContent = parseDiscordMessage(message);

			// Build the Discord→MC chat packet
			JsonNode customMessages = I18nManager.getCustomMessages();
			if (customMessages == null) return;

			// Check for reply/referenced message
			List<DiscordEventPacket.TextSegment> responseSegments = null;
			Message referencedMessage = message.getReferencedMessage();
			if (referencedMessage != null && getParsingBoolean("message_parsing.discord_to_minecraft.reply", null)) {
				String refEffectiveName;
				String refRoleColor;
				Member refMember = referencedMessage.getMember();
				if (refMember != null) {
					refEffectiveName = refMember.getEffectiveName();
					refRoleColor = getRoleColorString(refMember);
				} else {
					refEffectiveName = referencedMessage.getAuthor().getName();
					refRoleColor = "gray";
				}

				String refContent = parseDiscordMessage(referencedMessage);
				// Truncate long reply text
				if (refContent.length() > 50) {
					refContent = refContent.substring(0, 47) + "...";
				}

				JsonNode responseTemplate = customMessages.path("discord_to_minecraft").path("response");
				responseSegments = buildDiscordSegments(responseTemplate,
						Map.of("effective_name", refEffectiveName, "role_color", refRoleColor, "message", refContent));
			}

			// Determine the server label for the message
			String serverLabel = "Discord";
			String serverColor = "blue";

			// Build main chat segments from common.chat template
			JsonNode chatTemplate = customMessages.path("common").path("chat");
			List<DiscordEventPacket.TextSegment> segments = buildDiscordSegments(chatTemplate,
					Map.of("server", serverLabel, "server_color", serverColor,
							"effective_name", effectiveName, "role_color", roleColor,
							"message", parsedContent));

			// Handle mention notifications
			String mentionNotification = null;
			List<String> mentionedPlayerUuids = null;
			List<User> mentionedUsers = message.getMentions().getUsers();
			if (!mentionedUsers.isEmpty()) {
				Set<String> uuids = new HashSet<>();
				for (User mentionedUser : mentionedUsers) {
					List<String> mcUuids = LinkedAccountManager.getMinecraftUuidsByDiscordId(mentionedUser.getId());
					uuids.addAll(mcUuids);
				}
				if (!uuids.isEmpty()) {
					mentionedPlayerUuids = new ArrayList<>(uuids);
					mentionNotification = customMessages.path("discord_to_minecraft").path("mentioned").asText("")
							.replace("{effective_name}", effectiveName);
				}
			}

			DiscordEventPacket packet = new DiscordEventPacket(DiscordEventPacket.EventType.DISCORD_CHAT, segments);
			packet.responseSegments = responseSegments;
			packet.mentionNotification = mentionNotification;
			packet.mentionedPlayerUuids = mentionedPlayerUuids;

			NetworkManager.broadcastToClients(packet);
		} catch (Exception e) {
			LOGGER.error("Failed to process Discord message: {}", e.getMessage(), e);
		}
	}

	/**
	 * Checks if a TextChannel matches the given channel identifier (by name or ID).
	 *
	 * @param channel    The TextChannel to check.
	 * @param identifier The channel name or ID from config.
	 * @return true if the channel matches.
	 */
	private boolean matchesChannel(TextChannel channel, String identifier) {
		return channel.getName().equalsIgnoreCase(identifier) || channel.getId().equals(identifier);
	}

	/**
	 * Gets the role color of a member as a Minecraft-compatible color string.
	 *
	 * @param member The Discord member (may be null).
	 * @return A color string (e.g., "#RRGGBB") or "white" if no color is set.
	 */
	private String getRoleColorString(Member member) {
		if (member != null) {
			Color color = member.getColor();
			if (color != null) {
				return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
			}
		}
		return "white";
	}

	/**
	 * Parses a Discord message into a plain text representation suitable for Minecraft.
	 * Resolves mentions, custom emojis, attachments, stickers, etc. based on message_parsing config.
	 *
	 * @param message The Discord message to parse.
	 * @return The parsed message text.
	 */
	private String parseDiscordMessage(Message message) {
		String content = message.getContentRaw();

		// Resolve user mentions: <@123456> or <@!123456> → @Username
		if (getParsingBoolean("message_parsing.discord_to_minecraft.mentions.users",
				"message_parsing.discord_to_minecraft.mentions")) {
			for (User user : message.getMentions().getUsers()) {
				Member mentionedMember = message.getGuild().getMember(user);
				String displayName = mentionedMember != null ? mentionedMember.getEffectiveName() : user.getName();
				content = content.replace("<@" + user.getId() + ">", "@" + displayName);
				content = content.replace("<@!" + user.getId() + ">", "@" + displayName);
			}
		}

		// Resolve role mentions: <@&123456> → @RoleName
		if (getParsingBoolean("message_parsing.discord_to_minecraft.mentions.roles",
				"message_parsing.discord_to_minecraft.mentions")) {
			for (Role role : message.getMentions().getRoles()) {
				content = content.replace("<@&" + role.getId() + ">", "@" + role.getName());
			}
		}

		// Resolve channel mentions: <#123456> → #channel-name
		if (getParsingBoolean("message_parsing.discord_to_minecraft.mentions.channels",
				"message_parsing.discord_to_minecraft.mentions")) {
			for (net.dv8tion.jda.api.entities.channel.middleman.GuildChannel ch : message.getMentions().getChannels()) {
				content = content.replace("<#" + ch.getId() + ">", "#" + ch.getName());
			}
		}

		// Resolve custom emojis: <:name:123456> → :name:
		if (getParsingBoolean("message_parsing.discord_to_minecraft.custom_emojis", null)) {
			for (CustomEmoji emote : message.getMentions().getCustomEmojis()) {
				content = content.replace(emote.getAsMention(), ":" + emote.getName() + ":");
			}
		}

		// Handle Discord markdown conversion
		if (!isMarkdownEnabled()) {
			content = stripMarkdown(content);
		}

		// Append attachment info
		StringBuilder sb = new StringBuilder(content);
		if (getParsingBoolean("message_parsing.discord_to_minecraft.attachments.images",
				"message_parsing.discord_to_minecraft.attachments") ||
				getParsingBoolean("message_parsing.discord_to_minecraft.attachments.files",
						"message_parsing.discord_to_minecraft.attachments")) {
			for (Message.Attachment attachment : message.getAttachments()) {
				boolean isImage = attachment.isImage();
				boolean showImages = getParsingBoolean("message_parsing.discord_to_minecraft.attachments.images",
						"message_parsing.discord_to_minecraft.attachments");
				boolean showFiles = getParsingBoolean("message_parsing.discord_to_minecraft.attachments.files",
						"message_parsing.discord_to_minecraft.attachments");

				if ((isImage && showImages) || (!isImage && showFiles)) {
					if (!sb.isEmpty()) {
						sb.append(" ");
					}
					sb.append("[").append(attachment.getFileName()).append("]");
				}
			}
		}

		// Append sticker info
		if (getParsingBoolean("message_parsing.discord_to_minecraft.stickers", null)) {
			for (StickerItem sticker : message.getStickers()) {
				if (!sb.isEmpty()) {
					sb.append(" ");
				}
				sb.append("[Sticker: ").append(sticker.getName()).append("]");
			}
		}

		return sb.toString();
	}

	/**
	 * Gets a boolean config value with fallback to a parent key.
	 * This supports both the old flat config format and the new nested format.
	 *
	 * @param path     The specific (fine-grained) config path.
	 * @param fallback The parent (flat) config path, or null if no fallback.
	 * @return true if the feature is enabled.
	 */
	private boolean getParsingBoolean(String path, String fallback) {
		Boolean value = ConfigManager.getBoolean(path);
		if (value != null) return value;
		if (fallback != null) {
			value = ConfigManager.getBoolean(fallback);
			if (value != null) return value;
		}
		return true; // Default to enabled
	}

	/**
	 * Checks whether any markdown parsing is enabled in the config.
	 *
	 * @return true if at least one markdown feature is enabled.
	 */
	private boolean isMarkdownEnabled() {
		// Check fine-grained settings first
		String[] markdownKeys = {"bold", "italic", "underline", "strikethrough", "code", "code_block", "spoiler", "block_quote", "heading"};
		for (String key : markdownKeys) {
			Boolean val = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.markdown." + key);
			if (val != null) return val; // If any key is configured, use it
		}
		// Fall back to the flat "markdown" boolean
		Boolean flat = ConfigManager.getBoolean("message_parsing.discord_to_minecraft.markdown");
		return flat == null || flat;
	}

	/**
	 * Strips Discord markdown formatting from text.
	 *
	 * @param text The text to strip.
	 * @return The text without markdown formatting.
	 */
	private String stripMarkdown(String text) {
		// Remove bold, italic, underline, strikethrough, code, spoiler markers
		text = text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "$1"); // bold italic
		text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");       // bold
		text = text.replaceAll("\\*(.+?)\\*", "$1");             // italic
		text = text.replaceAll("__(.+?)__", "$1");               // underline
		text = text.replaceAll("~~(.+?)~~", "$1");               // strikethrough
		text = text.replaceAll("\\|\\|(.+?)\\|\\|", "$1");       // spoiler
		text = text.replaceAll("`{3}\\w*\\n?([\\s\\S]*?)`{3}", "$1"); // code block
		text = text.replaceAll("`(.+?)`", "$1");                 // inline code
		return text;
	}

	/**
	 * Builds styled text segments from a custom_messages template node for Discord events.
	 * Replaces all placeholders in text and color fields.
	 *
	 * @param templateNode The JSON array node from custom_messages.
	 * @param placeholders The placeholders map.
	 * @return A list of TextSegment objects.
	 */
	private List<DiscordEventPacket.TextSegment> buildDiscordSegments(JsonNode templateNode, Map<String, String> placeholders) {
		List<DiscordEventPacket.TextSegment> segments = new ArrayList<>();
		if (templateNode == null || !templateNode.isArray()) return segments;

		for (JsonNode segNode : templateNode) {
			String text = segNode.path("text").asText("");
			boolean bold = segNode.path("bold").asBoolean(false);
			String color = segNode.path("color").asText("white");

			for (Map.Entry<String, String> entry : placeholders.entrySet()) {
				text = text.replace("{" + entry.getKey() + "}", entry.getValue());
				color = color.replace("{" + entry.getKey() + "}", entry.getValue());
			}

			segments.add(new DiscordEventPacket.TextSegment(text, bold, color));
		}
		return segments;
	}

	/**
	 * Broadcasts a Discord command execution notification to all Minecraft clients.
	 * Uses the "discord_to_minecraft.command" custom_messages template.
	 *
	 * @param member  The Discord member who executed the command (may be null).
	 * @param user    The Discord user who executed the command.
	 * @param command The command string that was executed.
	 */
	private void broadcastDiscordCommandToMinecraft(Member member, User user, String command) {
		if (!Boolean.TRUE.equals(ConfigManager.getBoolean("broadcasts.discord_to_minecraft.command"))) {
			return;
		}

		try {
			JsonNode customMessages = I18nManager.getCustomMessages();
			if (customMessages == null) return;

			String effectiveName = member != null ? member.getEffectiveName() : user.getName();
			String roleColor = getRoleColorString(member);

			JsonNode commandTemplate = customMessages.path("discord_to_minecraft").path("command");
			List<DiscordEventPacket.TextSegment> segments = buildDiscordSegments(commandTemplate,
					Map.of("effective_name", effectiveName, "role_color", roleColor, "command", command));

			if (!segments.isEmpty()) {
				DiscordEventPacket packet = new DiscordEventPacket(DiscordEventPacket.EventType.DISCORD_CHAT, segments);
				NetworkManager.broadcastToClients(packet);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to broadcast Discord command to Minecraft: {}", e.getMessage(), e);
		}
	}
}
