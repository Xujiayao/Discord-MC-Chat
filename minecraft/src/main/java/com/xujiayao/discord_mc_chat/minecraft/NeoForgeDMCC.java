package com.xujiayao.discord_mc_chat.minecraft;

import com.xujiayao.discord_mc_chat.DMCC;
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
	}
}
