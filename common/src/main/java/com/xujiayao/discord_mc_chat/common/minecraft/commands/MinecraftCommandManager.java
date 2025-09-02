package com.xujiayao.discord_mc_chat.common.minecraft.commands;

import com.xujiayao.discord_mc_chat.common.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.i18n.TranslationService;
import com.xujiayao.discord_mc_chat.common.linking.AccountLinkingManager;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

/**
 * Minecraft命令管理器
 * 负责处理DMCC相关的Minecraft命令
 * 
 * @author Xujiayao
 */
public class MinecraftCommandManager {
    
    private static final Logger LOGGER = new Logger();
    
    private final ConfigManager configManager;
    private final TranslationService translationService;
    private final AccountLinkingManager linkingManager;
    
    public MinecraftCommandManager() {
        this.configManager = ConfigManager.getInstance();
        this.translationService = TranslationService.getInstance();
        this.linkingManager = AccountLinkingManager.getInstance();
    }
    
    /**
     * 处理DMCC命令
     * 这个方法将被Mixin调用
     * 
     * @param playerName 执行命令的玩家
     * @param subCommand 子命令
     * @param args 命令参数
     * @return 命令执行结果消息
     */
    public String handleCommand(String playerName, String subCommand, String[] args) {
        try {
            switch (subCommand.toLowerCase()) {
                case "help":
                    return handleHelpCommand(playerName, args);
                case "info":
                    return handleInfoCommand(playerName, args);
                case "stats":
                    return handleStatsCommand(playerName, args);
                case "update":
                    return handleUpdateCommand(playerName, args);
                case "whitelist":
                    return handleWhitelistCommand(playerName, args);
                case "reload":
                    return handleReloadCommand(playerName, args);
                case "link":
                    return handleLinkCommand(playerName, args);
                default:
                    return translationService.translate("error.invalid_argument") + ": " + subCommand;
            }
        } catch (Exception e) {
            LOGGER.error("处理Minecraft命令时发生错误", e);
            return translationService.translate("error.command_failed");
        }
    }
    
    /**
     * 处理help命令
     */
    private String handleHelpCommand(String playerName, String[] args) {
        StringBuilder help = new StringBuilder();
        help.append("§6=== DMCC 帮助 ===§r\n");
        help.append("§a/dmcc help§r - ").append(translationService.translate("command.help.description")).append("\n");
        help.append("§a/dmcc info§r - ").append(translationService.translate("command.info.description")).append("\n");
        help.append("§a/dmcc stats§r - ").append(translationService.translate("command.stats.description")).append("\n");
        help.append("§a/dmcc update§r - ").append(translationService.translate("command.update.description")).append("\n");
        help.append("§a/dmcc whitelist§r - ").append(translationService.translate("command.whitelist.description")).append("\n");
        help.append("§a/dmcc link§r - ").append(translationService.translate("command.link.description")).append("\n");
        
        // 管理员命令
        if (isAdmin(playerName)) {
            help.append("§c=== 管理员命令 ===§r\n");
            help.append("§c/dmcc reload§r - ").append(translationService.translate("command.reload.description")).append("\n");
        }
        
        return help.toString();
    }
    
    /**
     * 处理info命令
     */
    private String handleInfoCommand(String playerName, String[] args) {
        // TODO: 获取实际的服务器信息
        StringBuilder info = new StringBuilder();
        info.append("§6=== 服务器信息 ===§r\n");
        info.append("§aTPS: §f20.0\n");
        info.append("§aMSPT: §f50.0ms\n");
        info.append("§a在线玩家: §f1/20\n");
        info.append("§a内存使用: §f512MB/2048MB\n");
        
        return info.toString();
    }
    
    /**
     * 处理stats命令
     */
    private String handleStatsCommand(String playerName, String[] args) {
        // TODO: 实现统计功能
        return "§e统计功能正在开发中...";
    }
    
    /**
     * 处理update命令
     */
    private String handleUpdateCommand(String playerName, String[] args) {
        // TODO: 实现更新检查功能
        return "§e更新检查功能正在开发中...";
    }
    
    /**
     * 处理whitelist命令
     */
    private String handleWhitelistCommand(String playerName, String[] args) {
        if (!isAdmin(playerName) && configManager.getSecurityConfig().isWhitelistRequiresAdmin()) {
            return "§c" + translationService.translate("error.no_permission");
        }
        
        // TODO: 实现白名单功能
        return "§e白名单功能正在开发中...";
    }
    
    /**
     * 处理reload命令
     */
    private String handleReloadCommand(String playerName, String[] args) {
        if (!isAdmin(playerName)) {
            return "§c" + translationService.translate("error.no_permission");
        }
        
        try {
            configManager.reloadConfigs();
            return "§a" + translationService.translate("discord.command.reload.success");
        } catch (Exception e) {
            LOGGER.error("重载配置失败", e);
            return "§c" + translationService.translate("discord.command.reload.failed");
        }
    }
    
    /**
     * 处理link命令
     */
    private String handleLinkCommand(String playerName, String[] args) {
        if (!configManager.getAccountLinkingConfig().isEnable()) {
            return "§c账户链接功能未启用";
        }
        
        try {
            // 获取玩家UUID（这需要在实际的Minecraft环境中实现）
            String playerUuid = getPlayerUuid(playerName);
            
            if (linkingManager.isMinecraftLinked(playerUuid)) {
                return "§c您的账户已经链接到Discord";
            }
            
            String linkCode = linkingManager.generateLinkCode(playerUuid, playerName);
            int expiryMinutes = configManager.getAccountLinkingConfig().getLinkCodeExpiryMinutes();
            
            return String.format("§a链接码已生成: §f%s\n§7请在Discord中使用 /linkaccount %s 来链接您的账户\n§7链接码将在 %d 分钟后过期", 
                                linkCode, linkCode, expiryMinutes);
            
        } catch (Exception e) {
            LOGGER.error("生成链接码失败", e);
            return "§c生成链接码失败: " + e.getMessage();
        }
    }
    
    /**
     * 检查玩家是否为管理员
     * 
     * @param playerName 玩家名称
     * @return 是否为管理员
     */
    private boolean isAdmin(String playerName) {
        // TODO: 实现管理员检查逻辑
        // 这可能需要检查OP权限或者其他权限系统
        return false; // 临时返回false
    }
    
    /**
     * 获取玩家UUID
     * 这个方法需要在实际的Minecraft环境中实现
     * 
     * @param playerName 玩家名称
     * @return 玩家UUID
     */
    private String getPlayerUuid(String playerName) {
        // TODO: 在实际的Minecraft环境中实现UUID获取
        // 这可能需要通过Mixin或者其他方式获取玩家对象
        return "00000000-0000-0000-0000-000000000000"; // 临时返回虚拟UUID
    }
    
    /**
     * 检查命令是否可用
     * 
     * @param subCommand 子命令
     * @return 是否可用
     */
    public boolean isCommandAvailable(String subCommand) {
        switch (subCommand.toLowerCase()) {
            case "help":
            case "info":
            case "stats":
            case "update":
                return true;
            case "whitelist":
                return true; // 权限检查在执行时进行
            case "reload":
                return true; // 权限检查在执行时进行
            case "link":
                return configManager.getAccountLinkingConfig().isEnable();
            default:
                return false;
        }
    }
    
    /**
     * 获取命令的使用方法
     * 
     * @param subCommand 子命令
     * @return 使用方法描述
     */
    public String getCommandUsage(String subCommand) {
        String key = "command." + subCommand.toLowerCase() + ".usage";
        if (translationService.hasTranslation(key)) {
            return translationService.translate(key);
        }
        return "/dmcc " + subCommand;
    }
}