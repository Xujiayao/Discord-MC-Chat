package com.xujiayao.discord_mc_chat.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Webhook配置类
 * 
 * @author Xujiayao
 */
public class WebhookConfig {
    
    @JsonProperty("use")
    private boolean use = true;
    
    @JsonProperty("avatar_api")
    private String avatarApi = "https://mc-heads.net/avatar/{player_uuid}.png";
    
    @JsonProperty("name_template")
    private String nameTemplate = "{player_name}";
    
    public boolean isUse() { return use; }
    public void setUse(boolean use) { this.use = use; }
    
    public String getAvatarApi() { return avatarApi; }
    public void setAvatarApi(String avatarApi) { this.avatarApi = avatarApi; }
    
    public String getNameTemplate() { return nameTemplate; }
    public void setNameTemplate(String nameTemplate) { this.nameTemplate = nameTemplate; }
}