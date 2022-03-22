package top.xujiayao.mcdiscordchat.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.exception.ExceptionUtils;

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
				// TODO
				// String result = Objects.requireNonNull(response.body()).string();
				String result = "{\"version\":\"1.12.1\", \"changelog\":\"# 更新日志 Changelog\\n\\nMCDiscordChat 1.12.1 for Minecraft 1.17.x/1.18.x - 2022/3/5\\n\\n## 新特性 New Features\\n\\nN/A\\n\\n## 更改 Changes\\n\\n- 修复使用 1.18.2 时 Mixin 注入失败的问题\\n  Fix Mixin injection failure when using 1.18.2\\n  @Xujiayao (#16)\\n\\n## 移除 Removed\\n\\nN/A\\n\\n## 详细信息 Detailed Information\\n\\nhttps://github.com/Xujiayao/MCDiscordChat/compare/1.12.0...1.12.1\"}";

				JsonObject latestJson = new Gson().fromJson(result, JsonObject.class);
				String latestVersion = latestJson.get("version").getAsString();

				StringBuilder text = new StringBuilder();

				if (!latestVersion.equals(VERSION)) {
					text.append(CONFIG.generic.useEngInsteadOfChin ? "**A new version is available!**" : "**新版本可用！**");
					text.append("\n\n");
					text.append("MCDiscordChat **").append(VERSION).append("** -> **").append(latestVersion).append("**");
					text.append("\n\n");
					text.append(CONFIG.generic.useEngInsteadOfChin ? "Download link: https://github.com/Xujiayao/MCDiscordChat/blob/master/README.md#Download" : "下载链接：https://github.com/Xujiayao/MCDiscordChat/blob/master/README_CN.md#%E4%B8%8B%E8%BD%BD");
					text.append("\n\n");
					text.append("```");
					text.append("\n");
					text.append(latestJson.get("changelog").getAsString());
					text.append("\n");
					text.append("```");
					text.append("\n");
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
		// TODO
		// JsonObject json = new Gson().fromJson(IOUtils.toString(new URI("jar:file:" + Main.class.getProtectionDomain().getCodeSource().getLocation().getPath() + "!/fabric.mod.json"), StandardCharsets.UTF_8), JsonObject.class);
		JsonObject json = new Gson().fromJson("{\n" +
				"  \"schemaVersion\": 1,\n" +
				"  \"id\": \"mcdiscordchat\",\n" +
				"  \"version\": \"1.18-1.12.1\",\n" +
				"  \"name\": \"MCDiscordChat\",\n" +
				"  \"description\": \"MCDiscordChat (MCDC), a practical and powerful Fabric Minecraft <> Discord chat bridge inspired by BRForgers/DisFabric\",\n" +
				"  \"authors\": [\n" +
				"    \"Xujiayao\"\n" +
				"  ],\n" +
				"  \"contact\": {\n" +
				"    \"homepage\": \"https://blog.xujiayao.top/posts/4ba0a17a/\",\n" +
				"    \"issues\": \"https://github.com/Xujiayao/MCDiscordChat/issues\",\n" +
				"    \"sources\": \"https://github.com/Xujiayao/MCDiscordChat\"\n" +
				"  },\n" +
				"  \"license\": \"MIT\",\n" +
				"  \"icon\": \"assets/mcdiscordchat/icon.png\",\n" +
				"  \"environment\": \"server\",\n" +
				"  \"entrypoints\": {\n" +
				"    \"server\": [\n" +
				"      \"top.xujiayao.mcdiscordchat.Main\"\n" +
				"    ]\n" +
				"  },\n" +
				"  \"mixins\": [\n" +
				"    \"mcdiscordchat.mixins.json\"\n" +
				"  ],\n" +
				"  \"depends\": {\n" +
				"    \"fabricloader\": \">=0.13.3\",\n" +
				"    \"fabric\": \"*\",\n" +
				"    \"minecraft\": \"1.18.x\",\n" +
				"    \"java\": \">=17\"\n" +
				"  }\n" +
				"}", JsonObject.class);

		VERSION = json.get("version").getAsString();
		VERSION = VERSION.substring(VERSION.indexOf("-") + 1);
	}
}
