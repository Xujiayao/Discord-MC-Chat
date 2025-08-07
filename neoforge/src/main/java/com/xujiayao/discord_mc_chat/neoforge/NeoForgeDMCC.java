package com.xujiayao.discord_mc_chat.neoforge;

import com.xujiayao.discord_mc_chat.common.DMCC;
import net.neoforged.fml.common.Mod;

/**
 * @author Xujiayao
 */
@Mod("discord_mc_chat")
public class NeoForgeDMCC {

	public NeoForgeDMCC() {
		DMCC.init("NeoForge");
	}
}
