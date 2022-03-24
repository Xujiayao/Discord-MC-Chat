package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static top.xujiayao.mcdiscordchat.Main.CHANNEL;
import static top.xujiayao.mcdiscordchat.Main.CONFIG;
import static top.xujiayao.mcdiscordchat.Main.HTTP_CLIENT;
import static top.xujiayao.mcdiscordchat.Main.LOGGER;
import static top.xujiayao.mcdiscordchat.Main.TEXTS;
import static top.xujiayao.mcdiscordchat.Main.VERSION;

/**
 * @author Xujiayao
 */
public class Utils {

	public static String adminsMentionString() {
		StringBuilder text = new StringBuilder();

		for (String id : CONFIG.generic.adminsIds) {
			text.append("<@").append(id).append("> ");
		}

		return text.toString();
	}

	public static void checkUpdate(boolean isManualCheck) {
		try {
			Request request = new Request.Builder()
					.url("https://cdn.jsdelivr.net/gh/Xujiayao/MCDiscordChat@master/update/version.json")
					.build();

			try (Response response = HTTP_CLIENT.newCall(request).execute()) {
				String result = Objects.requireNonNull(response.body()).string();

				JsonObject latestJson = new Gson().fromJson(result, JsonObject.class);
				String latestVersion = latestJson.get("version").getAsString();

				// TODO 发布2.1.0后这段代码可以删除
				if (latestJson.get("changelog") == null) {
					Request request1 = new Request.Builder()
							.url("https://cdn.jsdelivr.net/gh/Xujiayao/MCDiscordChat@master/update/version-temp.json")
							.build();

					try (Response response1 = HTTP_CLIENT.newCall(request1).execute()) {
						result = Objects.requireNonNull(response1.body()).string();
					}

					latestJson = new Gson().fromJson(result, JsonObject.class);
					latestVersion = latestJson.get("version").getAsString();
				}

				StringBuilder text = new StringBuilder();

				if (!latestVersion.equals(VERSION)) {
					text.append(CONFIG.generic.useEngInsteadOfChin ? "**A new version is available!**" : "**新版本可用！**");
					text.append("\n\n");
					text.append("MCDiscordChat **").append(VERSION).append("** -> **").append(latestVersion).append("**");
					text.append("\n\n");
					text.append(CONFIG.generic.useEngInsteadOfChin ? "Download link: https://github.com/Xujiayao/MCDiscordChat/blob/master/README.md#Download" : "下载链接：https://github.com/Xujiayao/MCDiscordChat/blob/master/README_CN.md#%E4%B8%8B%E8%BD%BD");
					text.append("\n\n");
					text.append(latestJson.get("changelog").getAsString());
					text.append("\n\n");
					text.append(adminsMentionString());

					CHANNEL.sendMessage(text).queue();
				} else if (isManualCheck) {
					text.append("MCDiscordChat **").append(VERSION).append("**");
					text.append("\n\n");
					text.append(CONFIG.generic.useEngInsteadOfChin ? "**MCDiscordChat is up to date!**" : "**当前版本已经是最新版本！**");

					CHANNEL.sendMessage(text).queue();
				}
			}
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public static void reloadTexts() {
		if (CONFIG.generic.useEngInsteadOfChin) {
			TEXTS = new Texts(CONFIG.textsEN.serverStarted,
					CONFIG.textsEN.serverStopped,
					CONFIG.textsEN.joinServer,
					CONFIG.textsEN.leftServer,
					CONFIG.textsEN.deathMessage,
					CONFIG.textsEN.advancementTask,
					CONFIG.textsEN.advancementChallenge,
					CONFIG.textsEN.advancementGoal,
					CONFIG.textsEN.highMspt,
					CONFIG.textsEN.consoleLogMessage);
		} else {
			TEXTS = new Texts(CONFIG.textsZH.serverStarted,
					CONFIG.textsZH.serverStopped,
					CONFIG.textsZH.joinServer,
					CONFIG.textsZH.leftServer,
					CONFIG.textsZH.deathMessage,
					CONFIG.textsZH.advancementTask,
					CONFIG.textsZH.advancementChallenge,
					CONFIG.textsZH.advancementGoal,
					CONFIG.textsZH.highMspt,
					CONFIG.textsZH.consoleLogMessage);
		}
	}

	public static void setMcdcVersion() {
		JsonObject json;
		try {
			json = new Gson().fromJson(IOUtils.toString(new URI("jar:file:" + Utils.class.getProtectionDomain().getCodeSource().getLocation().getPath() + "!/fabric.mod.json"), StandardCharsets.UTF_8), JsonObject.class);
		} catch (Exception e) {
			json = new Gson().fromJson("""
					{
					  "schemaVersion": 1,
					  "id": "mcdiscordchat",
					  "version": "1.18-2.0.0-alpha.1",
					  "name": "MCDiscordChat",
					  "description": "MCDiscordChat (MCDC), a practical and powerful Fabric Minecraft <> Discord chat bridge inspired by BRForgers/DisFabric",
					  "authors": [
					    "Xujiayao"
					  ],
					  "contact": {
					    "homepage": "https://blog.xujiayao.top/posts/4ba0a17a/",
					    "issues": "https://github.com/Xujiayao/MCDiscordChat/issues",
					    "sources": "https://github.com/Xujiayao/MCDiscordChat"
					  },
					  "license": "MIT",
					  "icon": "assets/mcdiscordchat/icon.png",
					  "environment": "server",
					  "entrypoints": {
					    "server": [
					      "top.xujiayao.mcdiscordchat.Main"
					    ]
					  },
					  "mixins": [
					    "mcdiscordchat.mixins.json"
					  ],
					  "depends": {
					    "fabricloader": ">=0.13.3",
					    "fabric": "*",
					    "minecraft": "1.18.x",
					    "java": ">=17"
					  }
					}""", JsonObject.class);
		}

		VERSION = json.get("version").getAsString();
		VERSION = VERSION.substring(VERSION.indexOf("-") + 1);
	}
}
