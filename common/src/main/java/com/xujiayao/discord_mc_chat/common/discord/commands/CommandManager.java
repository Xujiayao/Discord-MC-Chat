package com.xujiayao.discord_mc_chat.common.discord.commands;

import com.xujiayao.discord_mc_chat.common.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.discord.commands.impl.*;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discord命令管理器
 * 负责注册和处理Discord斜杠命令
 * 
 * @author Xujiayao
 */
public class CommandManager extends ListenerAdapter {
    
    private static final Logger LOGGER = new Logger();
    
    private final JDA jda;
    private final ConfigManager configManager;
    private final Map<String, DiscordCommand> commands = new HashMap<>();
    
    public CommandManager(JDA jda) {
        this.jda = jda;
        this.configManager = ConfigManager.getInstance();
        
        // 注册事件监听器
        jda.addEventListener(this);
    }
    
    /**
     * 注册所有命令
     */
    public void registerCommands() {
        LOGGER.info("注册Discord命令...");
        
        // 注册命令实现
        registerCommand(new HelpCommand());
        registerCommand(new InfoCommand());
        registerCommand(new StatsCommand());
        registerCommand(new UpdateCommand());
        registerCommand(new WhitelistCommand());
        registerCommand(new ConsoleCommand());
        registerCommand(new LogCommand());
        registerCommand(new ReloadCommand());
        registerCommand(new StopCommand());
        registerCommand(new StartCommand());
        registerCommand(new LinkAccountCommand());
        
        // 构建命令数据列表
        List<CommandData> commandDataList = new ArrayList<>();
        for (DiscordCommand command : commands.values()) {
            commandDataList.add(command.getCommandData());
        }
        
        // 向Discord注册命令
        jda.updateCommands().addCommands(commandDataList).queue(
            success -> LOGGER.info("成功注册 {} 个Discord命令", commandDataList.size()),
            failure -> LOGGER.error("注册Discord命令失败", failure)
        );
    }
    
    /**
     * 注册单个命令
     * 
     * @param command 命令实例
     */
    private void registerCommand(DiscordCommand command) {
        commands.put(command.getName(), command);
        LOGGER.debug("注册命令: {}", command.getName());
    }
    
    /**
     * 处理斜杠命令交互事件
     * 
     * @param event 命令交互事件
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        DiscordCommand command = commands.get(commandName);
        
        if (command == null) {
            event.reply("未知命令: " + commandName).setEphemeral(true).queue();
            return;
        }
        
        try {
            // 检查权限
            if (!hasPermission(event, command)) {
                event.reply("您没有权限执行此命令。").setEphemeral(true).queue();
                return;
            }
            
            // 执行命令
            command.execute(event);
            
            LOGGER.info("用户 {} 执行了命令: {}", 
                event.getUser().getAsTag(), commandName);
            
        } catch (Exception e) {
            LOGGER.error("执行命令 {} 时发生错误", commandName, e);
            
            String errorMessage = "执行命令时发生错误";
            if (event.isAcknowledged()) {
                event.getHook().editOriginal(errorMessage).queue();
            } else {
                event.reply(errorMessage).setEphemeral(true).queue();
            }
        }
    }
    
    /**
     * 检查用户是否有权限执行命令
     * 
     * @param event 命令事件
     * @param command 命令实例
     * @return 是否有权限
     */
    private boolean hasPermission(SlashCommandInteractionEvent event, DiscordCommand command) {
        String commandName = command.getName();
        String requiredPermission = configManager.getSecurityConfig()
            .getCommandPermissions().get(commandName);
        
        if (requiredPermission == null || "everyone".equals(requiredPermission)) {
            return true;
        }
        
        String userId = event.getUser().getId();
        
        // 检查是否为管理员
        if ("admin".equals(requiredPermission)) {
            return configManager.getSecurityConfig().getAdminsIds().contains(userId);
        }
        
        // 检查角色权限
        if (event.isFromGuild()) {
            var member = event.getMember();
            if (member != null) {
                var roleHierarchy = configManager.getSecurityConfig().getRoleHierarchy();
                
                switch (requiredPermission) {
                    case "moderator":
                        return member.getRoles().stream()
                            .anyMatch(role -> roleHierarchy.getModerator().contains(role.getId()) ||
                                            roleHierarchy.getAdmin().contains(role.getId()));
                    case "trusted":
                        return member.getRoles().stream()
                            .anyMatch(role -> roleHierarchy.getTrusted().contains(role.getId()) ||
                                            roleHierarchy.getModerator().contains(role.getId()) ||
                                            roleHierarchy.getAdmin().contains(role.getId()));
                }
            }
        }
        
        return false;
    }
    
    /**
     * 获取已注册的命令数量
     * 
     * @return 命令数量
     */
    public int getCommandCount() {
        return commands.size();
    }
    
    /**
     * 获取命令列表
     * 
     * @return 命令名称列表
     */
    public List<String> getCommandNames() {
        return new ArrayList<>(commands.keySet());
    }
}