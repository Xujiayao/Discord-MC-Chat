package com.xujiayao.discord_mc_chat;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.xujiayao.discord_mc_chat.gui.GUI;
import net.fabricmc.loader.api.FabricLoader;

import javax.swing.*;
import java.util.Locale;

/**
 * @author Xujiayao
 */
public class Main {

	public static final String VERSION = FabricLoader.getInstance().getModContainer("discord-mc-chat").orElseThrow().getMetadata().getVersion().getFriendlyString();

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			FlatIntelliJLaf.setup();

			GUI gui = new GUI();
			gui.setVisible(true);
		});
	}
}
