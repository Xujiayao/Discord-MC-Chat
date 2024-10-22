package com.xujiayao.discord_mc_chat.gui;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Objects;

/**
 * @author Xujiayao
 */
public class Main {

	public static final String VERSION;

	static {
		String version = null;
		try (Reader reader = new InputStreamReader(Objects.requireNonNull(Main.class.getResourceAsStream("/fabric.mod.json")))) {
			version = new Gson().fromJson(reader, JsonObject.class).get("version").getAsString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		VERSION = version;
	}


	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			FlatIntelliJLaf.setup();

			GUI gui = new GUI();
			gui.setVisible(true);
		});
	}
}
