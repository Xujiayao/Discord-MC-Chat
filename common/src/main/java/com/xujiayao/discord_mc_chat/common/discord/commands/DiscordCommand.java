package com.xujiayao.discord_mc_chat.common.discord.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * Discord命令接口
 * 
 * @author Xujiayao
 */
public interface DiscordCommand {
    
    /**
     * 获取命令名称
     * 
     * @return 命令名称
     */
    String getName();
    
    /**
     * 获取命令描述
     * 
     * @return 命令描述
     */
    String getDescription();
    
    /**
     * 获取命令数据
     * 
     * @return 命令数据
     */
    CommandData getCommandData();
    
    /**
     * 执行命令
     * 
     * @param event 命令交互事件
     */
    void execute(SlashCommandInteractionEvent event);
}