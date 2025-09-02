package com.xujiayao.discord_mc_chat.common.discord;

import com.xujiayao.discord_mc_chat.common.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.core.RunModeManager;
import com.xujiayao.discord_mc_chat.common.discord.commands.CommandManager;
import com.xujiayao.discord_mc_chat.common.discord.listeners.MessageListener;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.util.concurrent.CompletableFuture;

/**
 * Discord客户端管理器
 * 负责管理Discord连接、监听器和命令
 * 
 * @author Xujiayao
 */
public class DiscordClient {
    
    private static final Logger LOGGER = new Logger();
    private static DiscordClient INSTANCE;
    
    private final ConfigManager configManager;
    private final RunModeManager runModeManager;
    
    private JDA jda;
    private CommandManager commandManager;
    private MessageListener messageListener;
    
    private boolean connected = false;
    private TextChannel mainChannel;
    private TextChannel consoleLogChannel;
    private TextChannel updateNotificationChannel;
    
    private DiscordClient() {
        this.configManager = ConfigManager.getInstance();
        this.runModeManager = RunModeManager.getInstance();
    }
    
    public static DiscordClient getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DiscordClient();
        }
        return INSTANCE;
    }
    
    /**
     * 初始化Discord客户端
     * 
     * @return 初始化结果的Future
     */
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String botToken = configManager.getConfig().getBotToken();
                if (botToken == null || botToken.isEmpty()) {
                    LOGGER.error("Discord机器人令牌未配置！");
                    return false;
                }
                
                LOGGER.info("正在连接Discord...");
                
                // 构建JDA实例
                JDABuilder builder = JDABuilder.createDefault(botToken)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS
                    );
                
                // 设置机器人状态
                setupBotActivity(builder);
                
                // 创建JDA实例
                jda = builder.build();
                
                // 等待JDA准备就绪
                jda.awaitReady();
                
                // 初始化组件
                initializeComponents();
                
                // 设置频道引用
                setupChannelReferences();
                
                connected = true;
                LOGGER.info("Discord连接成功！机器人用户: {}", jda.getSelfUser().getAsTag());
                
                return true;
                
            } catch (Exception e) {
                LOGGER.error("Discord连接失败", e);
                return false;
            }
        });
    }
    
    /**
     * 设置机器人活动状态
     * 
     * @param builder JDA构建器
     */
    private void setupBotActivity(JDABuilder builder) {
        var botStatus = configManager.getConfig().getBotStatus();
        
        if (botStatus.isShowServerStatus()) {
            if (!botStatus.getPlayingActivity().isEmpty()) {
                String activityText = formatActivityText(botStatus.getPlayingActivity());
                builder.setActivity(Activity.playing(activityText));
            } else if (!botStatus.getListeningActivity().isEmpty()) {
                String activityText = formatActivityText(botStatus.getListeningActivity());
                builder.setActivity(Activity.listening(activityText));
            }
        }
    }
    
    /**
     * 格式化活动文本
     * 
     * @param template 活动文本模板
     * @return 格式化后的文本
     */
    private String formatActivityText(String template) {
        // TODO: 实现占位符替换
        // 例如：{online_player_count}, {max_player_count} 等
        return template.replace("{online_player_count}", "0")
                      .replace("{max_player_count}", "20");
    }
    
    /**
     * 初始化Discord组件
     */
    private void initializeComponents() {
        LOGGER.info("初始化Discord组件...");
        
        // 初始化命令管理器
        commandManager = new CommandManager(jda);
        commandManager.registerCommands();
        
        // 初始化消息监听器
        messageListener = new MessageListener();
        jda.addEventListener(messageListener);
        
        LOGGER.info("Discord组件初始化完成");
    }
    
    /**
     * 设置频道引用
     */
    private void setupChannelReferences() {
        var channels = configManager.getConfig().getChannels();
        
        // 主聊天频道
        if (!channels.getMain().isEmpty()) {
            mainChannel = jda.getTextChannelById(channels.getMain());
            if (mainChannel == null) {
                LOGGER.warn("找不到主聊天频道: {}", channels.getMain());
            } else {
                LOGGER.info("主聊天频道: #{}", mainChannel.getName());
            }
        }
        
        // 控制台日志频道
        if (!channels.getConsoleLog().isEmpty()) {
            consoleLogChannel = jda.getTextChannelById(channels.getConsoleLog());
            if (consoleLogChannel == null) {
                LOGGER.warn("找不到控制台日志频道: {}", channels.getConsoleLog());
            } else {
                LOGGER.info("控制台日志频道: #{}", consoleLogChannel.getName());
            }
        }
        
        // 更新通知频道
        if (!channels.getUpdateNotification().isEmpty()) {
            updateNotificationChannel = jda.getTextChannelById(channels.getUpdateNotification());
            if (updateNotificationChannel == null) {
                LOGGER.warn("找不到更新通知频道: {}", channels.getUpdateNotification());
            } else {
                LOGGER.info("更新通知频道: #{}", updateNotificationChannel.getName());
            }
        }
    }
    
    /**
     * 关闭Discord客户端
     */
    public void shutdown() {
        if (jda != null) {
            LOGGER.info("正在关闭Discord连接...");
            
            // 更新机器人状态为离线
            updateBotStatus("离线");
            
            // 发送关闭通知
            sendServerShutdownNotification();
            
            // 关闭JDA
            jda.shutdown();
            connected = false;
            
            LOGGER.info("Discord连接已关闭");
        }
    }
    
    /**
     * 更新机器人状态
     * 
     * @param status 状态文本
     */
    public void updateBotStatus(String status) {
        if (jda != null) {
            jda.getPresence().setActivity(Activity.playing(status));
        }
    }
    
    /**
     * 发送服务器启动通知
     */
    public void sendServerStartNotification() {
        if (mainChannel != null) {
            String message = configManager.getMessagesConfig().getEvents().getServerStarted();
            mainChannel.sendMessage(message).queue();
        }
    }
    
    /**
     * 发送服务器关闭通知
     */
    public void sendServerShutdownNotification() {
        if (mainChannel != null) {
            String message = configManager.getMessagesConfig().getEvents().getServerStopped();
            mainChannel.sendMessage(message).queue();
        }
    }
    
    /**
     * 发送消息到主频道
     * 
     * @param message 消息内容
     */
    public void sendMessageToMainChannel(String message) {
        if (mainChannel != null && message != null && !message.isEmpty()) {
            mainChannel.sendMessage(message).queue();
        }
    }
    
    /**
     * 发送消息到控制台日志频道
     * 
     * @param message 消息内容
     */
    public void sendMessageToConsoleChannel(String message) {
        if (consoleLogChannel != null && message != null && !message.isEmpty()) {
            consoleLogChannel.sendMessage(message).queue();
        }
    }
    
    /**
     * 发送消息到更新通知频道
     * 
     * @param message 消息内容
     */
    public void sendMessageToUpdateChannel(String message) {
        if (updateNotificationChannel != null && message != null && !message.isEmpty()) {
            updateNotificationChannel.sendMessage(message).queue();
        }
    }
    
    /**
     * 检查是否已连接
     * 
     * @return 连接状态
     */
    public boolean isConnected() {
        return connected && jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }
    
    /**
     * 获取JDA实例
     * 
     * @return JDA实例
     */
    public JDA getJda() {
        return jda;
    }
    
    /**
     * 获取命令管理器
     * 
     * @return 命令管理器
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }
    
    /**
     * 获取消息监听器
     * 
     * @return 消息监听器
     */
    public MessageListener getMessageListener() {
        return messageListener;
    }
    
    /**
     * 获取主频道
     * 
     * @return 主频道
     */
    public TextChannel getMainChannel() {
        return mainChannel;
    }
    
    /**
     * 获取控制台日志频道
     * 
     * @return 控制台日志频道
     */
    public TextChannel getConsoleLogChannel() {
        return consoleLogChannel;
    }
    
    /**
     * 获取更新通知频道
     * 
     * @return 更新通知频道
     */
    public TextChannel getUpdateNotificationChannel() {
        return updateNotificationChannel;
    }
}