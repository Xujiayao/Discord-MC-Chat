package io.gitee.xujiayao147.mcDiscordChatBridge.objects;

public class Stats {

    private String name;
    private String uuid;
    private String content;

    public Stats(String name, String uuid, String content) {
        this.name = name;
        this.uuid = uuid;
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public String getUuid() {
        return uuid;
    }

    public String getContent() {
        return content;
    }
}
