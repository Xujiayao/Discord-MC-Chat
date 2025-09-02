package com.xujiayao.discord_mc_chat.common.discord.listeners;

import com.xujiayao.discord_mc_chat.common.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.core.RunModeManager;
import com.xujiayao.discord_mc_chat.common.formatting.MessageFormatter;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Discord消息监听器
 * 处理从Discord接收到的消息并转发到Minecraft
 * 
 * @author Xujiayao
 */
public class MessageListener extends ListenerAdapter {
    
    private static final Logger LOGGER = new Logger();
    
    private final ConfigManager configManager;
    private final MessageFormatter messageFormatter;
    private final RunModeManager runModeManager;
    
    public MessageListener() {
        this.configManager = ConfigManager.getInstance();
        this.messageFormatter = MessageFormatter.getInstance();
        this.runModeManager = RunModeManager.getInstance();
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // 忽略机器人消息
        if (event.getAuthor().isBot()) {
            return;
        }
        
        // 只处理指定频道的消息
        if (!isValidChannel(event.getChannel())) {
            return;
        }
        
        // 检查是否启用聊天消息广播
        if (!configManager.getMessagesConfig().getBroadcast().isChatMessages()) {
            return;
        }
        
        try {
            processMessage(event);
        } catch (Exception e) {
            LOGGER.error("处理Discord消息时发生错误", e);
        }
    }
    
    /**
     * 检查是否为有效的聊天频道
     * 
     * @param channel 频道
     * @return 是否有效
     */
    private boolean isValidChannel(Object channel) {
        if (!(channel instanceof TextChannel)) {
            return false;
        }
        
        String channelId = ((TextChannel) channel).getId();
        String mainChannelId = configManager.getConfig().getChannels().getMain();
        
        return channelId.equals(mainChannelId);
    }
    
    /**
     * 处理消息
     * 
     * @param event 消息事件
     */
    private void processMessage(MessageReceivedEvent event) {
        Message message = event.getMessage();
        User author = event.getAuthor();
        
        // 获取消息内容
        String content = message.getContentRaw();
        
        // 处理附件
        if (!message.getAttachments().isEmpty()) {
            content += " " + processAttachments(message);
        }
        
        // 处理消息引用
        if (message.getReferencedMessage() != null) {
            content = processMessageReference(message) + " " + content;
        }
        
        // 格式化消息
        String formattedMessage = messageFormatter.discordToMinecraft(content);
        
        // 获取发送者信息
        String senderName = getSenderName(author, event);
        String roleColor = getSenderRoleColor(event);
        
        // 转发到Minecraft（仅在模组模式下）
        if (runModeManager.isModMode()) {
            forwardToMinecraft(senderName, formattedMessage, roleColor);
        } else {
            // 在独立模式下，记录消息到日志
            LOGGER.info("[Discord] <{}> {}", senderName, formattedMessage);
        }
        
        LOGGER.debug("处理Discord消息: {} -> {}", author.getAsTag(), formattedMessage);
    }
    
    /**
     * 处理附件
     * 
     * @param message 消息
     * @return 附件描述
     */
    private String processAttachments(Message message) {
        StringBuilder attachments = new StringBuilder();
        
        for (Message.Attachment attachment : message.getAttachments()) {
            if (attachments.length() > 0) {
                attachments.append(", ");
            }
            
            if (attachment.isImage()) {
                attachments.append("[图片: ").append(attachment.getFileName()).append("]");
            } else if (attachment.isVideo()) {
                attachments.append("[视频: ").append(attachment.getFileName()).append("]");
            } else {
                attachments.append("[文件: ").append(attachment.getFileName()).append("]");
            }
        }
        
        return attachments.toString();
    }
    
    /**
     * 处理消息引用
     * 
     * @param message 消息
     * @return 引用描述
     */
    private String processMessageReference(Message message) {
        Message referencedMessage = message.getReferencedMessage();
        if (referencedMessage != null) {
            String referencedAuthor = referencedMessage.getAuthor().getDisplayName();
            String referencedContent = referencedMessage.getContentRaw();
            
            // 限制引用内容长度
            if (referencedContent.length() > 50) {
                referencedContent = referencedContent.substring(0, 47) + "...";
            }
            
            return String.format("回复 %s: %s", referencedAuthor, referencedContent);
        }
        
        return "";
    }
    
    /**
     * 获取发送者名称
     * 
     * @param author 作者
     * @param event 事件
     * @return 发送者名称
     */
    private String getSenderName(User author, MessageReceivedEvent event) {
        boolean useNickname = configManager.getMessagesConfig().getFormatting().isUseServerNickname();
        
        if (useNickname && event.isFromGuild() && event.getMember() != null) {
            return event.getMember().getEffectiveName();
        }
        
        return author.getDisplayName();
    }
    
    /**
     * 获取发送者角色颜色
     * 
     * @param event 事件
     * @return 角色颜色（Minecraft颜色代码）
     */
    private String getSenderRoleColor(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getMember() == null) {
            return "gray";
        }
        
        // TODO: 实现基于Discord角色的颜色映射
        // 这里应该根据用户的最高角色颜色来确定Minecraft颜色代码
        
        return "white"; // 默认白色
    }
    
    /**
     * 转发消息到Minecraft
     * 
     * @param senderName 发送者名称
     * @param message 消息内容
     * @param roleColor 角色颜色
     */
    private void forwardToMinecraft(String senderName, String message, String roleColor) {
        // TODO: 实现消息转发到Minecraft
        // 这个方法需要在Minecraft模块中实现
        
        // 暂时记录到日志
        LOGGER.info("[Discord -> Minecraft] <{}> {}", senderName, message);
    }
}