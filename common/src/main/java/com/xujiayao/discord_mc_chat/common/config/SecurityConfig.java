package com.xujiayao.discord_mc_chat.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * 安全配置类
 * 
 * @author Xujiayao
 */
public class SecurityConfig {
    
    @JsonProperty("whitelist_requires_admin")
    private boolean whitelistRequiresAdmin = true;
    
    @JsonProperty("admins_ids")
    private List<String> adminsIds = new ArrayList<>();
    
    @JsonProperty("excluded_commands")
    private List<String> excludedCommands = Arrays.asList(
        "\\/msg (?!@a)(.*)",
        "\\/tell (?!@a)(.*)",
        "\\/w (?!@a)(.*)"
    );
    
    @JsonProperty("command_permissions")
    private Map<String, String> commandPermissions = createDefaultCommandPermissions();
    
    @JsonProperty("role_hierarchy")
    private RoleHierarchyConfig roleHierarchy = new RoleHierarchyConfig();
    
    public boolean isWhitelistRequiresAdmin() { return whitelistRequiresAdmin; }
    public void setWhitelistRequiresAdmin(boolean whitelistRequiresAdmin) { this.whitelistRequiresAdmin = whitelistRequiresAdmin; }
    
    public List<String> getAdminsIds() { return adminsIds; }
    public void setAdminsIds(List<String> adminsIds) { this.adminsIds = adminsIds; }
    
    public List<String> getExcludedCommands() { return excludedCommands; }
    public void setExcludedCommands(List<String> excludedCommands) { this.excludedCommands = excludedCommands; }
    
    public Map<String, String> getCommandPermissions() { return commandPermissions; }
    public void setCommandPermissions(Map<String, String> commandPermissions) { this.commandPermissions = commandPermissions; }
    
    public RoleHierarchyConfig getRoleHierarchy() { return roleHierarchy; }
    public void setRoleHierarchy(RoleHierarchyConfig roleHierarchy) { this.roleHierarchy = roleHierarchy; }
    
    private static Map<String, String> createDefaultCommandPermissions() {
        Map<String, String> permissions = new HashMap<>();
        permissions.put("help", "everyone");
        permissions.put("info", "everyone");
        permissions.put("stats", "everyone");
        permissions.put("update", "everyone");
        permissions.put("whitelist", "admin");
        permissions.put("console", "admin");
        permissions.put("log", "admin");
        permissions.put("reload", "admin");
        permissions.put("stop", "admin");
        permissions.put("start", "admin");
        permissions.put("linkaccount", "everyone");
        return permissions;
    }
    
    /**
     * 角色权限层级配置
     */
    public static class RoleHierarchyConfig {
        @JsonProperty("admin")
        private List<String> admin = new ArrayList<>();
        
        @JsonProperty("moderator")
        private List<String> moderator = new ArrayList<>();
        
        @JsonProperty("trusted")
        private List<String> trusted = new ArrayList<>();
        
        public List<String> getAdmin() { return admin; }
        public void setAdmin(List<String> admin) { this.admin = admin; }
        
        public List<String> getModerator() { return moderator; }
        public void setModerator(List<String> moderator) { this.moderator = moderator; }
        
        public List<String> getTrusted() { return trusted; }
        public void setTrusted(List<String> trusted) { this.trusted = trusted; }
    }
}