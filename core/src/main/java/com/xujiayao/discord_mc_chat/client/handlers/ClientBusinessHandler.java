package com.xujiayao.discord_mc_chat.client.handlers;

import com.xujiayao.discord_mc_chat.commands.CommandEvents;
import com.xujiayao.discord_mc_chat.network.Packet;
import com.xujiayao.discord_mc_chat.network.Packets;
import com.xujiayao.discord_mc_chat.utils.CryptoUtils;
import com.xujiayao.discord_mc_chat.utils.config.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.events.EventManager;
import com.xujiayao.discord_mc_chat.utils.i18n.I18nManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import static com.xujiayao.discord_mc_chat.Constants.LOGGER;

/**
 * Handles client-side business logic.
 *
 * @author Xujiayao
 */
public class ClientBusinessHandler extends SimpleChannelInboundHandler<Packet> {

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
		if (packet instanceof Packets.ServerChallenge(String challenge)) {
			String sharedSecret = ConfigManager.getString("multi_server.security.shared_secret");
			String responseHash = CryptoUtils.hmacSha256(sharedSecret, challenge);
			ctx.writeAndFlush(new Packets.ClientResponse(responseHash));
		} else if (packet instanceof Packets.HandshakeSuccess(String messageKey, String language)) {
			LOGGER.info(I18nManager.getDmccTranslation(messageKey));

			// Synchronize language with server
			if (!I18nManager.load(language)) {
				LOGGER.error("Failed to load language \"{}\" from server. DMCC may not function correctly.", language);
			}

		} else if (packet instanceof Packets.HandshakeFailure(String messageKey)) {
			LOGGER.error("Handshake with server failed: {}", I18nManager.getDmccTranslation(messageKey));
			LOGGER.error("This is a fatal error. DMCC will now shut down.");

			// Post a StopEvent to trigger a graceful, application-wide shutdown.
			ctx.close(); // Close the connection first
			EventManager.post(new CommandEvents.StopEvent());
		}
		// TODO: Handle other packet types
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		LOGGER.error("Exception caught in client handler", cause);
		ctx.close();
	}
}
