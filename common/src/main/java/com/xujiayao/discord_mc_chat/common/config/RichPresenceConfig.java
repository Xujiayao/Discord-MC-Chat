package com.xujiayao.discord_mc_chat.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rich Presence配置类
 * 
 * @author Xujiayao
 */
public class RichPresenceConfig {
    
    @JsonProperty("enable")
    private boolean enable = true;
    
    @JsonProperty("show_server_info")
    private boolean showServerInfo = true;
    
    @JsonProperty("show_player_activity")
    private boolean showPlayerActivity = true;
    
    @JsonProperty("enable_invite_button")
    private boolean enableInviteButton = true;
    
    @JsonProperty("custom_icon_set")
    private String customIconSet = "default";
    
    @JsonProperty("show_timestamps")
    private boolean showTimestamps = true;
    
    public boolean isEnable() { return enable; }
    public void setEnable(boolean enable) { this.enable = enable; }
    
    public boolean isShowServerInfo() { return showServerInfo; }
    public void setShowServerInfo(boolean showServerInfo) { this.showServerInfo = showServerInfo; }
    
    public boolean isShowPlayerActivity() { return showPlayerActivity; }
    public void setShowPlayerActivity(boolean showPlayerActivity) { this.showPlayerActivity = showPlayerActivity; }
    
    public boolean isEnableInviteButton() { return enableInviteButton; }
    public void setEnableInviteButton(boolean enableInviteButton) { this.enableInviteButton = enableInviteButton; }
    
    public String getCustomIconSet() { return customIconSet; }
    public void setCustomIconSet(String customIconSet) { this.customIconSet = customIconSet; }
    
    public boolean isShowTimestamps() { return showTimestamps; }
    public void setShowTimestamps(boolean showTimestamps) { this.showTimestamps = showTimestamps; }
}