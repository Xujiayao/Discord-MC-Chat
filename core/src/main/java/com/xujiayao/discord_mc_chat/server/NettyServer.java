package com.xujiayao.discord_mc_chat.server;

/**
 * Encapsulates the Netty server setup and lifecycle.
 *
 * @author Xujiayao
 */
public class NettyServer {

	private final String host;
	private final int port;

	public NettyServer(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public int start() {
		// The bound port would be returned here after starting the server.
		return -1;
	}

	public void stop() {
	}
}
