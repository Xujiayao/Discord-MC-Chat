package com.xujiayao.discord_mc_chat.client;

/**
 * Encapsulates the Netty client, connection, and reconnection logic.
 *
 * @author Xujiayao
 */
public class NettyClient {

	private final String host;
	private final int port;

	public NettyClient(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public boolean start() {
		return true;
	}

	public void stop() {
	}
}
