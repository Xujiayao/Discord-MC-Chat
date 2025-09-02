package com.xujiayao.discord_mc_chat.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;

/**
 * 消息格式配置类
 * 
 * @author Xujiayao
 */
public class MessagesConfig {
    
    @JsonProperty("broadcast")
    private BroadcastConfig broadcast = new BroadcastConfig();
    
    @JsonProperty("formatting")
    private FormattingConfig formatting = new FormattingConfig();
    
    @JsonProperty("discord_to_minecraft")
    private DiscordToMinecraftConfig discordToMinecraft = new DiscordToMinecraftConfig();
    
    @JsonProperty("minecraft_to_discord")
    private MinecraftToDiscordConfig minecraftToDiscord = new MinecraftToDiscordConfig();
    
    @JsonProperty("events")
    private EventsConfig events = new EventsConfig();
    
    @JsonProperty("channel_topics")
    private ChannelTopicsConfig channelTopics = new ChannelTopicsConfig();
    
    // Getters and setters
    public BroadcastConfig getBroadcast() { return broadcast; }
    public void setBroadcast(BroadcastConfig broadcast) { this.broadcast = broadcast; }
    
    public FormattingConfig getFormatting() { return formatting; }
    public void setFormatting(FormattingConfig formatting) { this.formatting = formatting; }
    
    public DiscordToMinecraftConfig getDiscordToMinecraft() { return discordToMinecraft; }
    public void setDiscordToMinecraft(DiscordToMinecraftConfig discordToMinecraft) { this.discordToMinecraft = discordToMinecraft; }
    
    public MinecraftToDiscordConfig getMinecraftToDiscord() { return minecraftToDiscord; }
    public void setMinecraftToDiscord(MinecraftToDiscordConfig minecraftToDiscord) { this.minecraftToDiscord = minecraftToDiscord; }
    
    public EventsConfig getEvents() { return events; }
    public void setEvents(EventsConfig events) { this.events = events; }
    
    public ChannelTopicsConfig getChannelTopics() { return channelTopics; }
    public void setChannelTopics(ChannelTopicsConfig channelTopics) { this.channelTopics = channelTopics; }
    
    /**
     * 广播设置
     */
    public static class BroadcastConfig {
        @JsonProperty("player_command_execution")
        private boolean playerCommandExecution = true;
        
        @JsonProperty("slash_command_execution")
        private boolean slashCommandExecution = true;
        
        @JsonProperty("server_start_stop")
        private boolean serverStartStop = true;
        
        @JsonProperty("player_join_leave")
        private boolean playerJoinLeave = true;
        
        @JsonProperty("death_messages")
        private boolean deathMessages = true;
        
        @JsonProperty("advancements")
        private boolean advancements = true;
        
        @JsonProperty("chat_messages")
        private boolean chatMessages = true;
        
        public boolean isPlayerCommandExecution() { return playerCommandExecution; }
        public void setPlayerCommandExecution(boolean playerCommandExecution) { this.playerCommandExecution = playerCommandExecution; }
        
        public boolean isSlashCommandExecution() { return slashCommandExecution; }
        public void setSlashCommandExecution(boolean slashCommandExecution) { this.slashCommandExecution = slashCommandExecution; }
        
        public boolean isServerStartStop() { return serverStartStop; }
        public void setServerStartStop(boolean serverStartStop) { this.serverStartStop = serverStartStop; }
        
        public boolean isPlayerJoinLeave() { return playerJoinLeave; }
        public void setPlayerJoinLeave(boolean playerJoinLeave) { this.playerJoinLeave = playerJoinLeave; }
        
        public boolean isDeathMessages() { return deathMessages; }
        public void setDeathMessages(boolean deathMessages) { this.deathMessages = deathMessages; }
        
        public boolean isAdvancements() { return advancements; }
        public void setAdvancements(boolean advancements) { this.advancements = advancements; }
        
        public boolean isChatMessages() { return chatMessages; }
        public void setChatMessages(boolean chatMessages) { this.chatMessages = chatMessages; }
    }
    
    /**
     * 格式化设置
     */
    public static class FormattingConfig {
        @JsonProperty("format_chat_messages")
        private boolean formatChatMessages = true;
        
        @JsonProperty("allowed_mentions")
        private List<String> allowedMentions = Arrays.asList("everyone", "users", "roles");
        
        @JsonProperty("use_server_nickname")
        private boolean useServerNickname = true;
        
        @JsonProperty("discord_newline_limit")
        private int discordNewlineLimit = 3;
        
        @JsonProperty("message_style")
        private String messageStyle = "embedded";
        
        public boolean isFormatChatMessages() { return formatChatMessages; }
        public void setFormatChatMessages(boolean formatChatMessages) { this.formatChatMessages = formatChatMessages; }
        
        public List<String> getAllowedMentions() { return allowedMentions; }
        public void setAllowedMentions(List<String> allowedMentions) { this.allowedMentions = allowedMentions; }
        
        public boolean isUseServerNickname() { return useServerNickname; }
        public void setUseServerNickname(boolean useServerNickname) { this.useServerNickname = useServerNickname; }
        
        public int getDiscordNewlineLimit() { return discordNewlineLimit; }
        public void setDiscordNewlineLimit(int discordNewlineLimit) { this.discordNewlineLimit = discordNewlineLimit; }
        
        public String getMessageStyle() { return messageStyle; }
        public void setMessageStyle(String messageStyle) { this.messageStyle = messageStyle; }
    }
    
    /**
     * Discord到Minecraft消息模板
     */
    public static class DiscordToMinecraftConfig {
        @JsonProperty("chat")
        private String chat = "[{\"text\":\"[Discord] \",\"bold\":true,\"color\":\"blue\"},{\"text\":\"<{name}> \",\"bold\":false,\"color\":\"{role_color}\"},{\"text\":\"{message}\",\"bold\":false,\"color\":\"gray\"}]";
        
        @JsonProperty("response")
        private String response = "[{\"text\":\"    ┌──── \",\"bold\":true,\"color\":\"dark_gray\"},{\"text\":\"<{name}> \",\"bold\":false,\"color\":\"{role_color}\"},{\"text\":\"{message}\",\"bold\":false,\"color\":\"dark_gray\"}]";
        
        @JsonProperty("command")
        private String command = "[{\"text\":\"{name} \",\"bold\":false,\"color\":\"{role_color}\"},{\"text\":\"执行了 {command} 命令！\",\"bold\":false,\"color\":\"gray\"}]";
        
        public String getChat() { return chat; }
        public void setChat(String chat) { this.chat = chat; }
        
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
    }
    
    /**
     * Minecraft到Discord消息模板
     */
    public static class MinecraftToDiscordConfig {
        @JsonProperty("without_webhook")
        private String withoutWebhook = "<{name}> {message}";
        
        @JsonProperty("embedded_template")
        private boolean embeddedTemplate = true;
        
        @JsonProperty("plain_template")
        private String plainTemplate = "<{name}> {message}";
        
        public String getWithoutWebhook() { return withoutWebhook; }
        public void setWithoutWebhook(String withoutWebhook) { this.withoutWebhook = withoutWebhook; }
        
        public boolean isEmbeddedTemplate() { return embeddedTemplate; }
        public void setEmbeddedTemplate(boolean embeddedTemplate) { this.embeddedTemplate = embeddedTemplate; }
        
        public String getPlainTemplate() { return plainTemplate; }
        public void setPlainTemplate(String plainTemplate) { this.plainTemplate = plainTemplate; }
    }
    
    /**
     * 事件消息
     */
    public static class EventsConfig {
        @JsonProperty("server_started")
        private String serverStarted = "**服务器已启动！**";
        
        @JsonProperty("server_stopped")
        private String serverStopped = "**服务器已关闭！**";
        
        @JsonProperty("player_join")
        private String playerJoin = "**{player_name} 加入了游戏**";
        
        @JsonProperty("player_left")
        private String playerLeft = "**{player_name} 离开了游戏**";
        
        @JsonProperty("death_message")
        private String deathMessage = "**{death_message}**";
        
        @JsonProperty("advancement")
        private AdvancementConfig advancement = new AdvancementConfig();
        
        @JsonProperty("high_mspt")
        private String highMspt = "**服务器 MSPT ({mspt}) 高于 {mspt_limit}！**";
        
        public String getServerStarted() { return serverStarted; }
        public void setServerStarted(String serverStarted) { this.serverStarted = serverStarted; }
        
        public String getServerStopped() { return serverStopped; }
        public void setServerStopped(String serverStopped) { this.serverStopped = serverStopped; }
        
        public String getPlayerJoin() { return playerJoin; }
        public void setPlayerJoin(String playerJoin) { this.playerJoin = playerJoin; }
        
        public String getPlayerLeft() { return playerLeft; }
        public void setPlayerLeft(String playerLeft) { this.playerLeft = playerLeft; }
        
        public String getDeathMessage() { return deathMessage; }
        public void setDeathMessage(String deathMessage) { this.deathMessage = deathMessage; }
        
        public AdvancementConfig getAdvancement() { return advancement; }
        public void setAdvancement(AdvancementConfig advancement) { this.advancement = advancement; }
        
        public String getHighMspt() { return highMspt; }
        public void setHighMspt(String highMspt) { this.highMspt = highMspt; }
        
        public static class AdvancementConfig {
            @JsonProperty("task")
            private String task = "**{player_name} 达成了进度 [{advancement}]**\\n*{description}*";
            
            @JsonProperty("challenge")
            private String challenge = "**{player_name} 完成了挑战 [{advancement}]**\\n*{description}*";
            
            @JsonProperty("goal")
            private String goal = "**{player_name} 达成了目标 [{advancement}]**\\n*{description}*";
            
            public String getTask() { return task; }
            public void setTask(String task) { this.task = task; }
            
            public String getChallenge() { return challenge; }
            public void setChallenge(String challenge) { this.challenge = challenge; }
            
            public String getGoal() { return goal; }
            public void setGoal(String goal) { this.goal = goal; }
        }
    }
    
    /**
     * 频道主题模板
     */
    public static class ChannelTopicsConfig {
        @JsonProperty("offline")
        private String offline = ":x: 服务器离线 | 最后更新于：<t:{last_update_time}:f>";
        
        @JsonProperty("online")
        private String online = ":white_check_mark: {online_player_count}/{max_player_count} 位玩家在线 | 服务器玩家总数：{unique_player_count} | 服务器于 <t:{server_started_time}:R> 启动 | 最后更新于：<t:{last_update_time}:f>";
        
        public String getOffline() { return offline; }
        public void setOffline(String offline) { this.offline = offline; }
        
        public String getOnline() { return online; }
        public void setOnline(String online) { this.online = online; }
    }
}