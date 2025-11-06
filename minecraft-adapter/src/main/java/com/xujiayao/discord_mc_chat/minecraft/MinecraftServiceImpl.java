package com.xujiayao.discord_mc_chat.minecraft;

import com.xujiayao.discord_mc_chat.client.MinecraftService;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEventHandler;

/**
 * Implementation of the MinecraftService for the ServiceLoader.
 *
 * @author Xujiayao
 */
public class MinecraftServiceImpl implements MinecraftService {

	@Override
	public void initialize() {
		MinecraftEventHandler.init();
	}
}
