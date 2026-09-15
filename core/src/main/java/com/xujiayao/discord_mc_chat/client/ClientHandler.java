package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.commands.CommandAutoCompleter;
import com.xujiayao.discord_mc_chat.commands.CommandManager;
import com.xujiayao.discord_mc_chat.commands.CommandSender;
import com.xujiayao.discord_mc_chat.commands.impl.UpdateCommand;
import com.xujiayao.discord_mc_chat.config.ConfigManager;
import com.xujiayao.discord_mc_chat.config.I18nManager;
import com.xujiayao.discord_mc_chat.network.NetworkManager;
import com.xujiayao.discord_mc_chat.network.message.TextSegment;
import com.xujiayao.discord_mc_chat.network.protocol.Packet;
import com.xujiayao.discord_mc_chat.network.protocol.Packets;
import com.xujiayao.discord_mc_chat.platform.Platform;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles client-side network events and handshake protocol.
 *
 * @author Xujiayao
 */
final class ClientHandler extends SimpleChannelInboundHandler<Packet> {

	private static final int CONSOLE_COMMAND_TIMEOUT_SECONDS = 10;

	private final NettyClient client;
	private final CompletableFuture<Boolean> initialLoginFuture;
	private boolean allowReconnect = true; // Default to true for network errors

	ClientHandler(NettyClient client, CompletableFuture<Boolean> initialLoginFuture) {
		this.client = client;
		this.initialLoginFuture = initialLoginFuture;
	}

	private static void logDiscordEventForConsole(Packets.DiscordRelay p) {
		if (p.replySegments() != null && !p.replySegments().isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(p.replySegments()));
		}
		if (p.segments() != null && !p.segments().isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(p.segments()));
		}
		if (p.eventType() == Packets.DiscordEventType.EDIT && p.editedMessageSegments() != null && !p.editedMessageSegments().isEmpty()) {
			LOGGER.info(TextSegment.toPlainText(p.editedMessageSegments()));
		}
	}

	private static void logMinecraftEventForConsole(Packets.MinecraftRelay p) {
		if (p.segments() != null && !p.segments().isEmpty()) {
			String plain = TextSegment.toPlainText(p.segments());
			if (p.componentPlaceholder() != null && !p.componentPlaceholder().isBlank() && plain.contains(p.componentPlaceholder())) {
				String replacement = p.componentText() != null ? p.componentText() : "";
				LOGGER.info(plain.replace(p.componentPlaceholder(), replacement));
			} else {
				LOGGER.info(plain);
			}
		} else if (p.componentJson() != null && !p.componentJson().isBlank()) {
			LOGGER.warn(p.componentJson());
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		ctx.writeAndFlush(new Packets.Handshake(client.getServerName(), Constants.VERSION, EnvironmentUtils.getMinecraftVersion()));
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		LOGGER.warn(I18nManager.getDmccTranslation("client.network.disconnected_generic"));

		// Trigger reconnection if this was not an intentional stop AND the server didn't explicitly reject us
		if (client.isRunning()) {
			if (allowReconnect) {
				LOGGER.warn(I18nManager.getDmccTranslation("client.network.reconnecting"));
				client.scheduleReconnect();
			} else {
				// If reconnect is disallowed (e.g. kicked/banned), stop the client.
				client.stop();
			}
		}
		// ELSE: If client.isRunning() is false, it means stop() was called externally (e.g. server shutdown).
		// We do NOT need to call client.stop() again here, as that would be redundant and potentially risky.
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		switch (packet) {
			case Packets.Challenge p -> ctx.writeAndFlush(new Packets.AuthResponse(
					CryptUtils.sha256(p.salt() + client.getSharedSecret())));
			case Packets.LoginSuccess p -> {
				I18nManager.load(p.language());
				Constants.OVERWRITE_MINECRAFT_SOURCE_MESSAGES.set(p.overwriteMinecraftSourceMessages());
				ConsoleLogTailer.updateEnabled(p.consoleForwardingEnabled());
				LOGGER.info(I18nManager.getDmccTranslation("client.network.connected"));

				if (!initialLoginFuture.isDone()) {
					initialLoginFuture.complete(true);
				}
			}
			case Packets.InfoRequest p -> ctx.writeAndFlush(
					NetworkManager.createResponsePacket()
							.withConnectionLatency(Math.max(0, System.currentTimeMillis() - p.sentAtMillis())));
			case Packets.LatencyPong p -> client.updateConnectionLatency(
					Math.max(0, System.currentTimeMillis() - p.sentAtMillis()));
			case Packets.CommandRequest p -> handleCommandRequest(ctx, p);
			case Packets.AutoCompleteRequest p -> handleAutoCompleteRequest(ctx, p);
			case Packets.CommandResult p -> {
				if (p.kind() == Packets.RpcKind.UPDATE_CHECK) {
					UpdateCommand.completeRequest(p.requestId(), p);
				} else {
					LOGGER.warn(I18nManager.getDmccTranslation("client.network.unexpected_packet", p.type().name()));
				}
			}
			// Handle link code response from server - notify the player
			case Packets.LinkResult p -> Platform.host().sendLinkCode(p.minecraftUuid(), p.code(), p.alreadyLinked(),
					p.discordName() != null ? p.discordName() : "");
			// Handle unlink response from server - notify the player
			case Packets.UnlinkResult p -> Platform.host().sendUnlinkResult(p.minecraftUuid(), p.success(),
					p.discordName() != null ? p.discordName() : "");
			// Handle OP sync from server - apply OP levels to Minecraft players
			case Packets.OpSync p -> Platform.host().applyOpLevels(p.opLevels());
			case Packets.DiscordRelay p -> {
				// Handle Discord event forwarded from server - render in Minecraft
				if ("multi_server_client".equals(ConfigManager.getMode())) {
					logDiscordEventForConsole(p);
				}
				switch (p.eventType()) {
					case CHAT -> Platform.host().broadcastDiscordChat(
							p.segments(),
							p.replySegments(),
							p.mentionNotificationText(),
							p.mentionNotificationStyle(),
							p.mentionedPlayerUuids(),
							p.mentionEveryone()
					);
					case COMMAND -> Platform.host().broadcastDiscordCommand(p.segments());
					case REACTION -> Platform.host().broadcastDiscordReaction(
							p.segments(),
							p.replySegments()
					);
					case EDIT -> Platform.host().broadcastDiscordEdit(
							p.segments(),
							p.replySegments(),
							p.editedMessageSegments()
					);
					case DELETE -> Platform.host().broadcastDiscordDelete(
							p.segments(),
							p.replySegments()
					);
				}
			}
			case Packets.MinecraftRelay p -> {
				if ("multi_server_client".equals(ConfigManager.getMode())) {
					logMinecraftEventForConsole(p);
				}

				Platform.host().broadcastMinecraftRelay(
						p.segments(),
						p.componentJson(),
						p.componentPlaceholder(),
						p.mentionNotificationText(),
						p.mentionNotificationStyle(),
						p.mentionedPlayerUuids(),
						p.mentionEveryone()
				);
			}
			case Packets.Disconnect p -> {
				// If we receive a Disconnect packet, it means the server explicitly rejected us.
				// In most cases (whitelist, auth fail, version mismatch), retrying immediately won't help.
				// So we disable reconnection.
				allowReconnect = false;

				String reason = I18nManager.getDmccTranslation(p.key(), (Object[]) p.args());
				LOGGER.error(I18nManager.getDmccTranslation("client.network.disconnected_reason", reason));

				if (!initialLoginFuture.isDone()) {
					initialLoginFuture.complete(false);
				}
				ctx.close();
			}
			case null, default ->
					LOGGER.warn(I18nManager.getDmccTranslation("client.network.unexpected_packet", packet == null ? "null" : packet.type().name()));
		}
	}

	/**
	 * Runs a command requested by the DMCC Server and reports the captured output back.
	 */
	private void handleCommandRequest(ChannelHandlerContext ctx, Packets.CommandRequest request) {
		switch (request.kind()) {
			case EXECUTE -> handleDmccCommand(ctx, request);
			case CONSOLE -> handleMinecraftCommand(ctx, request);
			case UPDATE_CHECK -> LOGGER.warn(I18nManager.getDmccTranslation("client.network.unexpected_packet", request.type().name()));
		}
	}

	private void handleDmccCommand(ChannelHandlerContext ctx, Packets.CommandRequest request) {
		StringBuilder responseBuilder = new StringBuilder();
		byte[][] fileDataHolder = new byte[1][];
		String[] fileNameHolder = new String[1];

		CommandSender captureSender = new CommandSender() {
			@Override
			public void reply(String message) {
				if (!responseBuilder.isEmpty()) {
					responseBuilder.append("\n");
				}
				responseBuilder.append(message);
			}

			@Override
			public void replyWithFile(String message, byte[] fileData, String fileName) {
				reply(message);
				fileDataHolder[0] = fileData;
				fileNameHolder[0] = fileName;
			}

			@Override
			public int getOpLevel() {
				return request.opLevel();
			}
		};

		try {
			CommandManager.executeAndWait(captureSender, request.input(), request.args())
					.whenComplete((_, ex) -> sendDmccResult(ctx, request, responseBuilder, fileDataHolder, fileNameHolder, ex));
		} catch (Exception e) {
			ctx.writeAndFlush(Packets.CommandResult.text(Packets.RpcKind.EXECUTE, request.requestId(),
					I18nManager.getDmccTranslation("commands.execution_failed", e.getMessage())));
		}
	}

	/**
	 * Sends the captured command output back, streaming any file payload as separate chunk frames first.
	 */
	private void sendDmccResult(ChannelHandlerContext ctx, Packets.CommandRequest request, StringBuilder responseBuilder,
								byte[][] fileDataHolder, String[] fileNameHolder, Throwable ex) {
		if (ex != null) {
			ctx.writeAndFlush(Packets.CommandResult.text(Packets.RpcKind.EXECUTE, request.requestId(),
					I18nManager.getDmccTranslation("commands.execution_failed", ex.getMessage())));
			return;
		}
		if (fileDataHolder[0] == null) {
			ctx.writeAndFlush(Packets.CommandResult.text(Packets.RpcKind.EXECUTE, request.requestId(), responseBuilder.toString()));
			return;
		}

		String fileName = client.getServerName() + "_" + fileNameHolder[0];
		for (Packets.CommandFileChunk chunk : Packets.CommandFileChunk.split(request.requestId(), fileName, fileDataHolder[0])) {
			ctx.writeAndFlush(chunk);
		}
		ctx.writeAndFlush(new Packets.CommandResult(Packets.RpcKind.EXECUTE, request.requestId(),
				responseBuilder.toString(), fileName));
	}

	private void handleMinecraftCommand(ChannelHandlerContext ctx, Packets.CommandRequest request) {
		StringBuilder responseBuilder = new StringBuilder();

		CommandSender captureSender = new CommandSender() {
			@Override
			public void reply(String message) {
				if (!responseBuilder.isEmpty()) {
					responseBuilder.append("\n");
				}
				responseBuilder.append(message);
			}

			@Override
			public int getOpLevel() {
				return request.opLevel();
			}
		};

		CompletableFuture<Void> completionFuture = new CompletableFuture<>();
		Platform.host().executeCommand(captureSender, request.input(), completionFuture);

		// Use the completion future with a timeout to send the response reliably
		completionFuture
				.orTimeout(CONSOLE_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.whenComplete((_, _) -> ctx.writeAndFlush(Packets.CommandResult.text(
						Packets.RpcKind.CONSOLE, request.requestId(), responseBuilder.toString())));
	}

	private void handleAutoCompleteRequest(ChannelHandlerContext ctx, Packets.AutoCompleteRequest request) {
		List<String> suggestions = new ArrayList<>();
		if (request.kind() == Packets.RpcKind.CONSOLE) {
			// Handle Minecraft command auto-complete via the platform host
			Platform.host().autoCompleteCommand(request.input(), request.opLevel(), suggestions);
		} else {
			// Handle DMCC command auto-complete with OP level filtering
			suggestions = CommandAutoCompleter.getSuggestions(request.input(), request.opLevel());
		}
		ctx.writeAndFlush(new Packets.AutoCompleteResult(request.kind(), suggestions));
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent e) {
			if (e.state() == IdleState.WRITER_IDLE) {
				ctx.writeAndFlush(new Packets.KeepAlive());
			}
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		LOGGER.error(I18nManager.getDmccTranslation("client.network.connect_failed"), cause);
		if (!initialLoginFuture.isDone()) {
			initialLoginFuture.complete(false);
		}
		ctx.close();
	}
}
