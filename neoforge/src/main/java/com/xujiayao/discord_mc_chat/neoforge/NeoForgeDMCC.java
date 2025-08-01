package com.xujiayao.discord_mc_chat.neoforge;

import com.xujiayao.discord_mc_chat.common.DMCC;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(NeoForgeDMCC.MOD_ID)
public class NeoForgeDMCC {

	// Define mod id in a common place for everything to reference
	public static final String MOD_ID = "discord_mc_chat";
	// Directly reference a slf4j logger
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// The constructor for the mod class is the first code that is run when your mod is loaded.
	// FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
	public NeoForgeDMCC() {
		LOGGER.info("Hello NeoForge world!");

		DMCC.init("NeoForge");
	}
}
