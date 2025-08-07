package com.xujiayao.discord_mc_chat.neoforge;

import com.xujiayao.discord_mc_chat.common.DMCC;
import net.neoforged.fml.common.Mod;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod("discord_mc_chat")
public class NeoForgeDMCC {

	// The constructor for the mod class is the first code that is run when your mod is loaded.
	// FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
	public NeoForgeDMCC() {
		DMCC.init("NeoForge");
	}
}
