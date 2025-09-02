package com.xujiayao.discord_mc_chat.common.minecraft;

import com.xujiayao.discord_mc_chat.common.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.core.RunModeManager;
import com.xujiayao.discord_mc_chat.common.minecraft.commands.MinecraftCommandManager;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

/**
 * Minecraft集成管理器
 * 负责管理Minecraft相关的功能集成
 * 
 * @author Xujiayao
 */
public class MinecraftIntegration {
    
    private static final Logger LOGGER = new Logger();
    private static MinecraftIntegration INSTANCE;
    
    private final ConfigManager configManager;
    private final RunModeManager runModeManager;
    
    private MinecraftCommandManager commandManager;
    private boolean initialized = false;
    
    private MinecraftIntegration() {
        this.configManager = ConfigManager.getInstance();
        this.runModeManager = RunModeManager.getInstance();
    }
    
    public static MinecraftIntegration getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MinecraftIntegration();
        }
        return INSTANCE;
    }
    
    /**
     * 初始化Minecraft集成
     */
    public void initialize() {
        if (initialized) {
            LOGGER.warn("Minecraft集成已经初始化，忽略重复初始化");
            return;
        }
        
        if (!runModeManager.isModMode()) {
            LOGGER.info("非模组模式，跳过Minecraft集成初始化");
            return;
        }
        
        LOGGER.info("初始化Minecraft集成...");
        
        try {
            // 初始化命令管理器
            initializeCommandManager();
            
            // 注册事件监听器（通过Mixin实现）
            registerEventListeners();
            
            initialized = true;
            LOGGER.info("Minecraft集成初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("Minecraft集成初始化失败", e);
            throw new RuntimeException("Minecraft集成初始化失败", e);
        }
    }
    
    /**
     * 初始化命令管理器
     */
    private void initializeCommandManager() {
        LOGGER.info("初始化Minecraft命令管理器...");
        commandManager = new MinecraftCommandManager();
        // 命令注册将在服务器启动后进行
    }
    
    /**
     * 注册事件监听器
     * 实际的事件监听通过Mixin实现
     */
    private void registerEventListeners() {
        LOGGER.info("注册Minecraft事件监听器...");
        // 事件监听器通过Mixin自动注册
        // 这里可以进行一些额外的初始化工作
    }
    
    /**
     * 处理玩家聊天消息
     * 这个方法将被Mixin调用
     * 
     * @param playerName 玩家名称
     * @param message 消息内容
     */
    public static void handlePlayerChat(String playerName, String message) {
        try {
            if (INSTANCE != null && INSTANCE.initialized) {
                INSTANCE.onPlayerChat(playerName, message);
            }
        } catch (Exception e) {
            LOGGER.error("处理玩家聊天消息时发生错误", e);
        }
    }
    
    /**
     * 处理玩家加入事件
     * 这个方法将被Mixin调用
     * 
     * @param playerName 玩家名称
     */
    public static void handlePlayerJoin(String playerName) {
        try {
            if (INSTANCE != null && INSTANCE.initialized) {
                INSTANCE.onPlayerJoin(playerName);
            }
        } catch (Exception e) {
            LOGGER.error("处理玩家加入事件时发生错误", e);
        }
    }
    
    /**
     * 处理玩家离开事件
     * 这个方法将被Mixin调用
     * 
     * @param playerName 玩家名称
     */
    public static void handlePlayerLeave(String playerName) {
        try {
            if (INSTANCE != null && INSTANCE.initialized) {
                INSTANCE.onPlayerLeave(playerName);
            }
        } catch (Exception e) {
            LOGGER.error("处理玩家离开事件时发生错误", e);
        }
    }
    
    /**
     * 处理玩家死亡事件
     * 这个方法将被Mixin调用
     * 
     * @param deathMessage 死亡消息
     */
    public static void handlePlayerDeath(String deathMessage) {
        try {
            if (INSTANCE != null && INSTANCE.initialized) {
                INSTANCE.onPlayerDeath(deathMessage);
            }
        } catch (Exception e) {
            LOGGER.error("处理玩家死亡事件时发生错误", e);
        }
    }
    
    /**
     * 处理玩家进度事件
     * 这个方法将被Mixin调用
     * 
     * @param playerName 玩家名称
     * @param advancement 进度名称
     * @param description 进度描述
     * @param type 进度类型（task, challenge, goal）
     */
    public static void handlePlayerAdvancement(String playerName, String advancement, String description, String type) {
        try {
            if (INSTANCE != null && INSTANCE.initialized) {
                INSTANCE.onPlayerAdvancement(playerName, advancement, description, type);
            }
        } catch (Exception e) {
            LOGGER.error("处理玩家进度事件时发生错误", e);
        }
    }
    
    /**
     * 处理服务器命令执行
     * 这个方法将被Mixin调用
     * 
     * @param command 命令内容
     * @param output 命令输出
     */
    public static void handleServerCommand(String command, String output) {
        try {
            if (INSTANCE != null && INSTANCE.initialized) {
                INSTANCE.onServerCommand(command, output);
            }
        } catch (Exception e) {
            LOGGER.error("处理服务器命令时发生错误", e);
        }
    }
    
    /**
     * 处理玩家聊天消息的内部方法
     */
    private void onPlayerChat(String playerName, String message) {
        if (!configManager.getMessagesConfig().getBroadcast().isChatMessages()) {
            return;
        }
        
        LOGGER.debug("玩家聊天: {} -> {}", playerName, message);
        
        // TODO: 转发到Discord
        // 这里需要调用Discord模块来发送消息
    }
    
    /**
     * 处理玩家加入的内部方法
     */
    private void onPlayerJoin(String playerName) {
        if (!configManager.getMessagesConfig().getBroadcast().isPlayerJoinLeave()) {
            return;
        }
        
        LOGGER.info("玩家加入: {}", playerName);
        
        // TODO: 发送到Discord
    }
    
    /**
     * 处理玩家离开的内部方法
     */
    private void onPlayerLeave(String playerName) {
        if (!configManager.getMessagesConfig().getBroadcast().isPlayerJoinLeave()) {
            return;
        }
        
        LOGGER.info("玩家离开: {}", playerName);
        
        // TODO: 发送到Discord
    }
    
    /**
     * 处理玩家死亡的内部方法
     */
    private void onPlayerDeath(String deathMessage) {
        if (!configManager.getMessagesConfig().getBroadcast().isDeathMessages()) {
            return;
        }
        
        LOGGER.info("玩家死亡: {}", deathMessage);
        
        // TODO: 发送到Discord
    }
    
    /**
     * 处理玩家进度的内部方法
     */
    private void onPlayerAdvancement(String playerName, String advancement, String description, String type) {
        if (!configManager.getMessagesConfig().getBroadcast().isAdvancements()) {
            return;
        }
        
        LOGGER.info("玩家进度: {} 达成了 {} ({})", playerName, advancement, type);
        
        // TODO: 发送到Discord
    }
    
    /**
     * 处理服务器命令的内部方法
     */
    private void onServerCommand(String command, String output) {
        if (!configManager.getMessagesConfig().getBroadcast().isSlashCommandExecution()) {
            return;
        }
        
        LOGGER.debug("服务器命令: {} -> {}", command, output);
        
        // TODO: 发送到Discord控制台频道
    }
    
    /**
     * 关闭Minecraft集成
     */
    public void shutdown() {
        if (initialized) {
            LOGGER.info("关闭Minecraft集成...");
            initialized = false;
            LOGGER.info("Minecraft集成已关闭");
        }
    }
    
    /**
     * 检查是否已初始化
     * 
     * @return 初始化状态
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 获取命令管理器
     * 
     * @return 命令管理器
     */
    public MinecraftCommandManager getCommandManager() {
        return commandManager;
    }
}