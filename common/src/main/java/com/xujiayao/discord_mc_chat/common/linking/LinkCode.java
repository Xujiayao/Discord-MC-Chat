package com.xujiayao.discord_mc_chat.common.linking;

/**
 * 链接码数据类
 * 
 * @author Xujiayao
 */
public class LinkCode {
    
    private final String code;
    private final String minecraftUuid;
    private final String minecraftName;
    private final long expirationTime;
    private final long creationTime;
    
    public LinkCode(String code, String minecraftUuid, String minecraftName, long expirationTime) {
        this.code = code;
        this.minecraftUuid = minecraftUuid;
        this.minecraftName = minecraftName;
        this.expirationTime = expirationTime;
        this.creationTime = System.currentTimeMillis();
    }
    
    /**
     * 检查链接码是否已过期
     * 
     * @return 是否过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
    
    /**
     * 获取剩余有效时间（分钟）
     * 
     * @return 剩余分钟数
     */
    public long getRemainingMinutes() {
        long remaining = expirationTime - System.currentTimeMillis();
        return Math.max(0, remaining / 60000);
    }
    
    // Getters
    public String getCode() { return code; }
    public String getMinecraftUuid() { return minecraftUuid; }
    public String getMinecraftName() { return minecraftName; }
    public long getExpirationTime() { return expirationTime; }
    public long getCreationTime() { return creationTime; }
}