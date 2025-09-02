package com.xujiayao.discord_mc_chat.common.discord.commands.impl;

import com.xujiayao.discord_mc_chat.common.DMCC;
import com.xujiayao.discord_mc_chat.common.discord.commands.DiscordCommand;
import com.xujiayao.discord_mc_chat.common.formatting.MessageFormatter;
import com.xujiayao.discord_mc_chat.common.i18n.TranslationService;
import com.xujiayao.discord_mc_chat.common.monitoring.MonitoringService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.*;

/**
 * 服务器信息命令实现
 * 
 * @author Xujiayao
 */
public class InfoCommand implements DiscordCommand {
    
    private final TranslationService translationService;
    private final MessageFormatter messageFormatter;
    
    public InfoCommand() {
        this.translationService = TranslationService.getInstance();
        this.messageFormatter = MessageFormatter.getInstance();
    }
    
    @Override
    public String getName() {
        return "info";
    }
    
    @Override
    public String getDescription() {
        return translationService.translate("command.info.description");
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), getDescription());
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // 获取服务器状态
        MonitoringService.ServerStatus status = MonitoringService.getInstance().getServerStatus();
        
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(translationService.translate("discord.command.info.title"));
        embed.setColor(Color.GREEN);
        
        // 在线玩家
        embed.addField(
            translationService.translate("discord.command.info.online_players"),
            status.getOnlinePlayerCount() + "/" + status.getMaxPlayerCount(),
            true
        );
        
        // TPS
        embed.addField(
            translationService.translate("discord.command.info.tps"),
            messageFormatter.formatNumber(status.getTps()),
            true
        );
        
        // MSPT
        embed.addField(
            translationService.translate("discord.command.info.mspt"),
            messageFormatter.formatNumber(status.getMspt()) + "ms",
            true
        );
        
        // 运行时间
        if (DMCC.getInstance() != null) {
            long uptime = DMCC.getInstance().getUptime();
            embed.addField(
                translationService.translate("discord.command.info.uptime"),
                messageFormatter.formatDuration(uptime),
                true
            );
        }
        
        // 内存使用情况
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        embed.addField(
            "内存使用",
            String.format("%.1f MB / %.1f MB", 
                usedMemory / 1024.0 / 1024.0,
                maxMemory / 1024.0 / 1024.0),
            true
        );
        
        embed.setFooter("最后更新: " + java.time.Instant.ofEpochMilli(status.getTimestamp()));
        embed.setTimestamp(java.time.Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
}