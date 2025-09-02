package com.xujiayao.discord_mc_chat.common.discord.commands.impl;

import com.xujiayao.discord_mc_chat.common.discord.commands.DiscordCommand;
import com.xujiayao.discord_mc_chat.common.i18n.TranslationService;
import com.xujiayao.discord_mc_chat.common.linking.AccountLinkingManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * 账户链接命令实现
 * 
 * @author Xujiayao
 */
public class LinkAccountCommand implements DiscordCommand {
    
    private final TranslationService translationService;
    private final AccountLinkingManager linkingManager;
    
    public LinkAccountCommand() {
        this.translationService = TranslationService.getInstance();
        this.linkingManager = AccountLinkingManager.getInstance();
    }
    
    @Override
    public String getName() {
        return "linkaccount";
    }
    
    @Override
    public String getDescription() {
        return "链接您的Discord账户到Minecraft账户";
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash(getName(), getDescription())
            .addOption(OptionType.STRING, "code", "链接码", true);
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String code = event.getOption("code").getAsString();
        String discordId = event.getUser().getId();
        String discordName = event.getUser().getAsTag();
        
        try {
            boolean success = linkingManager.linkAccount(code, discordId, discordName);
            
            if (success) {
                event.reply(translationService.translate("discord.command.linkaccount.success"))
                    .setEphemeral(true).queue();
            } else {
                event.reply(translationService.translate("discord.command.linkaccount.invalid"))
                    .setEphemeral(true).queue();
            }
            
        } catch (Exception e) {
            event.reply(translationService.translate("discord.command.linkaccount.failed"))
                .setEphemeral(true).queue();
        }
    }
}