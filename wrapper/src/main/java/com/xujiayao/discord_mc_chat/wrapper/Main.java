package com.xujiayao.discord_mc_chat.wrapper;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * @author Xujiayao
 */
public class Main {

	public static final String VERSION;
	public static final Logger LOGGER = LoggerFactory.getLogger("Discord-MC-Chat Wrapper");

	static {
		String version = null;
		try (Reader reader = new InputStreamReader(Objects.requireNonNull(Main.class.getResourceAsStream("/fabric.mod.json")))) {
			version = new Gson().fromJson(reader, JsonObject.class).get("version").getAsString();
		} catch (Exception e) {
			LOGGER.error("Exception", e);
		}
		VERSION = version;
	}


	public static void main(String[] args) {
		// Locale.setDefault(Locale.US);

		if (Desktop.isDesktopSupported()) {
			SwingUtilities.invokeLater(() -> {
				FlatIntelliJLaf.setup();

				GUI gui = new GUI();
				gui.setVisible(true);
			});
		} else {
			ResourceBundle bundle = PropertyResourceBundle.getBundle("lang/lang");

			String description2 = bundle.getString("description2").replaceAll("<html>|</html>", "");

			LOGGER.info("-----------------------------------------");
			LOGGER.info(bundle.getString("welcome"));
			LOGGER.info(MessageFormat.format(bundle.getString("version"), VERSION));
			LOGGER.info(bundle.getString("author"));
			LOGGER.info("");
			LOGGER.info(bundle.getString("description1").replaceAll("<html>|</html>", ""));
			LOGGER.info("");
			LOGGER.info(description2.substring(0, description2.indexOf("<")));
			LOGGER.info(description2.substring(description2.indexOf("<br>") + 8));
			LOGGER.info("");
			LOGGER.info(bundle.getString("description3").replaceAll("<html>|</html>", "").replaceAll("</font>", " (https://blog.xujiayao.com/posts/4ba0a17a/)").replaceAll("<(.*?)>", ""));
			LOGGER.info("");
			LOGGER.info(bundle.getString("description4").replaceAll("<html>|</html>", "").replaceAll("</font>", " (https://discord.gg/kbXkV6k2XU)").replaceAll("<(.*?)>", ""));
			LOGGER.info("-----------------------------------------");
		}
	}
}
// TODO icon size?