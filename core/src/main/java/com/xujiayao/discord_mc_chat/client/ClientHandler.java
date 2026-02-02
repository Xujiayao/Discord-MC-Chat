package com.xujiayao.discord_mc_chat.client;

import com.xujiayao.discord_mc_chat.Constants;
import com.xujiayao.discord_mc_chat.network.packets.AuthResponsePacket;
import com.xujiayao.discord_mc_chat.network.packets.ChallengePacket;
import com.xujiayao.discord_mc_chat.network.packets.DisconnectPacket;
import com.xujiayao.discord_mc_chat.network.packets.HandshakePacket;
import com.xujiayao.discord_mc_chat.network.packets.KeepAlivePacket;
import com.xujiayao.discord_mc_chat.network.packets.LoginSuccessPacket;
import com.xujiayao.discord_mc_chat.network.packets.Packet;
import com.xujiayao.discord_mc_chat.utils.CryptUtils;
import com.xujiayao.discord_mc_chat.utils.EnvironmentUtils;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.concurrent.CompletableFuture;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles client-side network events and handshake protocol.
 *
 * @author Xujiayao
 */
public class ClientHandler extends SimpleChannelInboundHandler<Packet> {

	private final NettyClient client;
	private final CompletableFuture<Boolean> initialLoginFuture;
	private boolean allowReconnect = true; // Default to true for network errors

	public ClientHandler(NettyClient client, CompletableFuture<Boolean> initialLoginFuture) {
		this.client = client;
		this.initialLoginFuture = initialLoginFuture;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		ctx.writeAndFlush(new HandshakePacket(client.getServerName(), Constants.VERSION, EnvironmentUtils.getMinecraftVersion()));
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
			case ChallengePacket p -> {
				String hash = CryptUtils.sha256(p.salt + client.getSharedSecret());
				ctx.writeAndFlush(new AuthResponsePacket(hash));
			}
			case LoginSuccessPacket p -> {
				I18nManager.load(p.language);
				LOGGER.info(I18nManager.getDmccTranslation("client.network.connected"));

				if (!initialLoginFuture.isDone()) {
					initialLoginFuture.complete(true);
				}
			}
			case DisconnectPacket p -> {
				// If we receive a DisconnectPacket, it means the server explicitly rejected us.
				// In most cases (whitelist, auth fail, version mismatch), retrying immediately won't help.
				// So we disable reconnection.
				allowReconnect = false;

				String reason = I18nManager.getDmccTranslation(p.key, p.args);
				LOGGER.error(I18nManager.getDmccTranslation("client.network.disconnected_reason", reason));

				if (!initialLoginFuture.isDone()) {
					initialLoginFuture.complete(false);
				}
				ctx.close();
			}
			case null, default -> LOGGER.warn(I18nManager.getDmccTranslation("client.network.unexpected_packet", packet == null ? "null" : packet.getClass().getSimpleName()));
		}
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent e) {
			if (e.state() == IdleState.WRITER_IDLE) {
				ctx.writeAndFlush(new KeepAlivePacket());
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
