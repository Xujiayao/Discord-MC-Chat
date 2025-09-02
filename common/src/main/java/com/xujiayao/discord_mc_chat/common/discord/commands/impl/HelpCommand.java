package com.xujiayao.discord_mc_chat.common.discord.commands.impl;

import com.xujiayao.discord_mc_chat.common.discord.commands.DiscordCommand;
import com.xujiayao.discord_mc_chat.common.i18n.TranslationService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.*;

/**
 * 帮助命令实现
 * 
 * @author Xujiayao
 */
public class HelpCommand implements DiscordCommand {
    
    private final TranslationService translationService;
    
    public HelpCommand() {
        this.translationService = TranslationService.getInstance();
    }
    
    @Override
    public String getName() {
        return "help";
    }
    
    @Override
    public String getDescription() {
        return translationService.translate("command.help.description");
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), getDescription());
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(translationService.translate("discord.command.help.title"));
        embed.setDescription(translationService.translate("discord.command.help.description"));
        embed.setColor(Color.BLUE);
        
        // 添加命令列表
        embed.addField("/help", translationService.translate("command.help.description"), false);
        embed.addField("/info", translationService.translate("command.info.description"), false);
        embed.addField("/stats", translationService.translate("command.stats.description"), false);
        embed.addField("/update", translationService.translate("command.update.description"), false);
        embed.addField("/whitelist", translationService.translate("command.whitelist.description"), false);
        embed.addField("/reload", translationService.translate("command.reload.description"), false);
        embed.addField("/link", translationService.translate("command.link.description"), false);
        
        // 管理员命令
        embed.addField("**管理员命令**", "", false);
        embed.addField("/console", "执行服务器命令", false);
        embed.addField("/log", "查看服务器日志", false);
        embed.addField("/stop", "停止服务器", false);
        embed.addField("/start", "启动服务器（仅独立模式）", false);
        
        embed.setFooter("DMCC v3.0.0");
        embed.setTimestamp(java.time.Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
}