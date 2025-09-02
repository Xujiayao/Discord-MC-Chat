package com.xujiayao.discord_mc_chat.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * 账户链接配置类
 * 
 * @author Xujiayao
 */
public class AccountLinkingConfig {
    
    @JsonProperty("enable")
    private boolean enable = true;
    
    @JsonProperty("link_code_expiry_minutes")
    private int linkCodeExpiryMinutes = 10;
    
    @JsonProperty("sync_role_colors")
    private boolean syncRoleColors = true;
    
    @JsonProperty("enable_mention_notifications")
    private boolean enableMentionNotifications = true;
    
    @JsonProperty("mention_notification_style")
    private String mentionNotificationStyle = "action_bar";
    
    @JsonProperty("permission_inheritance")
    private PermissionInheritanceConfig permissionInheritance = new PermissionInheritanceConfig();
    
    public boolean isEnable() { return enable; }
    public void setEnable(boolean enable) { this.enable = enable; }
    
    public int getLinkCodeExpiryMinutes() { return linkCodeExpiryMinutes; }
    public void setLinkCodeExpiryMinutes(int linkCodeExpiryMinutes) { this.linkCodeExpiryMinutes = linkCodeExpiryMinutes; }
    
    public boolean isSyncRoleColors() { return syncRoleColors; }
    public void setSyncRoleColors(boolean syncRoleColors) { this.syncRoleColors = syncRoleColors; }
    
    public boolean isEnableMentionNotifications() { return enableMentionNotifications; }
    public void setEnableMentionNotifications(boolean enableMentionNotifications) { this.enableMentionNotifications = enableMentionNotifications; }
    
    public String getMentionNotificationStyle() { return mentionNotificationStyle; }
    public void setMentionNotificationStyle(String mentionNotificationStyle) { this.mentionNotificationStyle = mentionNotificationStyle; }
    
    public PermissionInheritanceConfig getPermissionInheritance() { return permissionInheritance; }
    public void setPermissionInheritance(PermissionInheritanceConfig permissionInheritance) { this.permissionInheritance = permissionInheritance; }
    
    /**
     * 权限继承配置
     */
    public static class PermissionInheritanceConfig {
        @JsonProperty("enable")
        private boolean enable = true;
        
        @JsonProperty("role_to_permission_map")
        private Map<String, String> roleToPermissionMap = createDefaultRoleToPermissionMap();
        
        public boolean isEnable() { return enable; }
        public void setEnable(boolean enable) { this.enable = enable; }
        
        public Map<String, String> getRoleToPermissionMap() { return roleToPermissionMap; }
        public void setRoleToPermissionMap(Map<String, String> roleToPermissionMap) { this.roleToPermissionMap = roleToPermissionMap; }
        
        private static Map<String, String> createDefaultRoleToPermissionMap() {
            Map<String, String> map = new HashMap<>();
            map.put("admin_role_id", "op_level_4");
            map.put("mod_role_id", "op_level_2");
            map.put("builder_role_id", "custom_permission_node");
            return map;
        }
    }
}