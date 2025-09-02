package com.xujiayao.discord_mc_chat.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 主配置类
 * 
 * @author Xujiayao
 */
public class Config {
    
    @JsonProperty("language")
    private String language = "zh_cn";
    
    @JsonProperty("bot_token")
    private String botToken = "";
    
    @JsonProperty("channels")
    private ChannelConfig channels = new ChannelConfig();
    
    @JsonProperty("bot_status")
    private BotStatusConfig botStatus = new BotStatusConfig();
    
    @JsonProperty("multi_server")
    private MultiServerConfig multiServer = new MultiServerConfig();
    
    @JsonProperty("monitoring")
    private MonitoringConfig monitoring = new MonitoringConfig();
    
    @JsonProperty("updates")
    private UpdateConfig updates = new UpdateConfig();
    
    @JsonProperty("shutdown")
    private ShutdownConfig shutdown = new ShutdownConfig();
    
    // Getters and setters
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    
    public ChannelConfig getChannels() { return channels; }
    public void setChannels(ChannelConfig channels) { this.channels = channels; }
    
    public BotStatusConfig getBotStatus() { return botStatus; }
    public void setBotStatus(BotStatusConfig botStatus) { this.botStatus = botStatus; }
    
    public MultiServerConfig getMultiServer() { return multiServer; }
    public void setMultiServer(MultiServerConfig multiServer) { this.multiServer = multiServer; }
    
    public MonitoringConfig getMonitoring() { return monitoring; }
    public void setMonitoring(MonitoringConfig monitoring) { this.monitoring = monitoring; }
    
    public UpdateConfig getUpdates() { return updates; }
    public void setUpdates(UpdateConfig updates) { this.updates = updates; }
    
    public ShutdownConfig getShutdown() { return shutdown; }
    public void setShutdown(ShutdownConfig shutdown) { this.shutdown = shutdown; }
    
    /**
     * 频道配置
     */
    public static class ChannelConfig {
        @JsonProperty("main")
        private String main = "";
        
        @JsonProperty("console_log")
        private String consoleLog = "";
        
        @JsonProperty("update_notification")
        private String updateNotification = "";
        
        public String getMain() { return main; }
        public void setMain(String main) { this.main = main; }
        
        public String getConsoleLog() { return consoleLog; }
        public void setConsoleLog(String consoleLog) { this.consoleLog = consoleLog; }
        
        public String getUpdateNotification() { return updateNotification; }
        public void setUpdateNotification(String updateNotification) { this.updateNotification = updateNotification; }
    }
    
    /**
     * 机器人状态配置
     */
    public static class BotStatusConfig {
        @JsonProperty("show_server_status")
        private boolean showServerStatus = true;
        
        @JsonProperty("playing_activity")
        private String playingActivity = "Minecraft ({online_player_count}/{max_player_count})";
        
        @JsonProperty("listening_activity")
        private String listeningActivity = "";
        
        public boolean isShowServerStatus() { return showServerStatus; }
        public void setShowServerStatus(boolean showServerStatus) { this.showServerStatus = showServerStatus; }
        
        public String getPlayingActivity() { return playingActivity; }
        public void setPlayingActivity(String playingActivity) { this.playingActivity = playingActivity; }
        
        public String getListeningActivity() { return listeningActivity; }
        public void setListeningActivity(String listeningActivity) { this.listeningActivity = listeningActivity; }
    }
    
    /**
     * 多服务器配置
     */
    public static class MultiServerConfig {
        @JsonProperty("enable")
        private boolean enable = false;
        
        @JsonProperty("host")
        private boolean host = false;
        
        public boolean isEnable() { return enable; }
        public void setEnable(boolean enable) { this.enable = enable; }
        
        public boolean isHost() { return host; }
        public void setHost(boolean host) { this.host = host; }
    }
    
    /**
     * 监控配置
     */
    public static class MonitoringConfig {
        @JsonProperty("announce_high_mspt")
        private boolean announceHighMspt = true;
        
        @JsonProperty("mspt_check_interval")
        private long msptCheckInterval = 5000;
        
        @JsonProperty("mspt_limit")
        private double msptLimit = 50.0;
        
        @JsonProperty("update_channel_topic")
        private boolean updateChannelTopic = true;
        
        @JsonProperty("channel_topic_update_interval")
        private long channelTopicUpdateInterval = 600000;
        
        public boolean isAnnounceHighMspt() { return announceHighMspt; }
        public void setAnnounceHighMspt(boolean announceHighMspt) { this.announceHighMspt = announceHighMspt; }
        
        public long getMsptCheckInterval() { return msptCheckInterval; }
        public void setMsptCheckInterval(long msptCheckInterval) { this.msptCheckInterval = msptCheckInterval; }
        
        public double getMsptLimit() { return msptLimit; }
        public void setMsptLimit(double msptLimit) { this.msptLimit = msptLimit; }
        
        public boolean isUpdateChannelTopic() { return updateChannelTopic; }
        public void setUpdateChannelTopic(boolean updateChannelTopic) { this.updateChannelTopic = updateChannelTopic; }
        
        public long getChannelTopicUpdateInterval() { return channelTopicUpdateInterval; }
        public void setChannelTopicUpdateInterval(long channelTopicUpdateInterval) { this.channelTopicUpdateInterval = channelTopicUpdateInterval; }
    }
    
    /**
     * 更新配置
     */
    public static class UpdateConfig {
        @JsonProperty("notify_updates")
        private boolean notifyUpdates = true;
        
        @JsonProperty("mention_admins_for_updates")
        private boolean mentionAdminsForUpdates = true;
        
        public boolean isNotifyUpdates() { return notifyUpdates; }
        public void setNotifyUpdates(boolean notifyUpdates) { this.notifyUpdates = notifyUpdates; }
        
        public boolean isMentionAdminsForUpdates() { return mentionAdminsForUpdates; }
        public void setMentionAdminsForUpdates(boolean mentionAdminsForUpdates) { this.mentionAdminsForUpdates = mentionAdminsForUpdates; }
    }
    
    /**
     * 关闭配置
     */
    public static class ShutdownConfig {
        @JsonProperty("immediately")
        private boolean immediately = false;
        
        public boolean isImmediately() { return immediately; }
        public void setImmediately(boolean immediately) { this.immediately = immediately; }
    }
}