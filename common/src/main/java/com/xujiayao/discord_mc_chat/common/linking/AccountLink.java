package com.xujiayao.discord_mc_chat.common.linking;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 账户链接数据类
 * 
 * @author Xujiayao
 */
public class AccountLink {
    
    @JsonProperty("minecraft_uuid")
    private String minecraftUuid;
    
    @JsonProperty("minecraft_name")
    private String minecraftName;
    
    @JsonProperty("discord_id")
    private String discordId;
    
    @JsonProperty("discord_name")
    private String discordName;
    
    @JsonProperty("linked_time")
    private long linkedTime;
    
    @JsonProperty("last_update_time")
    private long lastUpdateTime;
    
    public AccountLink() {}
    
    public AccountLink(String minecraftUuid, String minecraftName, String discordId, String discordName) {
        this.minecraftUuid = minecraftUuid;
        this.minecraftName = minecraftName;
        this.discordId = discordId;
        this.discordName = discordName;
        this.linkedTime = System.currentTimeMillis();
        this.lastUpdateTime = this.linkedTime;
    }
    
    // Getters and setters
    public String getMinecraftUuid() { return minecraftUuid; }
    public void setMinecraftUuid(String minecraftUuid) { this.minecraftUuid = minecraftUuid; }
    
    public String getMinecraftName() { return minecraftName; }
    public void setMinecraftName(String minecraftName) { 
        this.minecraftName = minecraftName;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public String getDiscordId() { return discordId; }
    public void setDiscordId(String discordId) { this.discordId = discordId; }
    
    public String getDiscordName() { return discordName; }
    public void setDiscordName(String discordName) { 
        this.discordName = discordName;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public long getLinkedTime() { return linkedTime; }
    public void setLinkedTime(long linkedTime) { this.linkedTime = linkedTime; }
    
    public long getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
}