package com.xujiayao.discord_mc_chat.minecraft;

import com.xujiayao.discord_mc_chat.DMCC;
import com.xujiayao.discord_mc_chat.minecraft.events.MinecraftEventHandler;
import net.neoforged.fml.common.Mod;

/**
 * The entry point for NeoForge environment.
 *
 * @author Xujiayao
 */
@Mod("discord_mc_chat")
public class NeoForgeDMCC {

	/**
	 * Start NeoForge DMCC.
	 */
	public NeoForgeDMCC() {
		DMCC.init();

		// TODO Maybe this line should come first
		MinecraftEventHandler.init();
	}
}
