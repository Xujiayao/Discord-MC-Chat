package com.xujiayao.discord_mc_chat.common.core;

import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

/**
 * 运行模式管理器
 * 负责识别和管理不同的运行模式（模组模式/独立模式）
 * 
 * @author Xujiayao
 */
public class RunModeManager {
    
    private static final Logger LOGGER = new Logger();
    private static RunModeManager INSTANCE;
    
    private final RunMode runMode;
    private final String loaderName;
    
    private RunModeManager(RunMode runMode, String loaderName) {
        this.runMode = runMode;
        this.loaderName = loaderName;
    }
    
    public static RunModeManager getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("RunModeManager未初始化！请先调用initialize方法");
        }
        return INSTANCE;
    }
    
    /**
     * 初始化运行模式管理器
     * 
     * @param loaderName 加载器名称（"Fabric", "NeoForge", "Standalone"）
     */
    public static void initialize(String loaderName) {
        if (INSTANCE != null) {
            LOGGER.warn("RunModeManager已经初始化，忽略重复初始化");
            return;
        }
        
        RunMode mode = determineRunMode(loaderName);
        INSTANCE = new RunModeManager(mode, loaderName);
        
        LOGGER.info("运行模式已确定: {} (加载器: {})", mode.name(), loaderName);
    }
    
    /**
     * 根据加载器名称确定运行模式
     * 
     * @param loaderName 加载器名称
     * @return 运行模式
     */
    private static RunMode determineRunMode(String loaderName) {
        if ("Standalone".equals(loaderName)) {
            return RunMode.STANDALONE;
        } else if ("Fabric".equals(loaderName) || "NeoForge".equals(loaderName)) {
            return RunMode.MOD;
        } else {
            LOGGER.warn("未知的加载器名称: {}，默认使用模组模式", loaderName);
            return RunMode.MOD;
        }
    }
    
    /**
     * 获取当前运行模式
     * 
     * @return 运行模式
     */
    public RunMode getRunMode() {
        return runMode;
    }
    
    /**
     * 获取加载器名称
     * 
     * @return 加载器名称
     */
    public String getLoaderName() {
        return loaderName;
    }
    
    /**
     * 是否为模组模式
     * 
     * @return true如果是模组模式
     */
    public boolean isModMode() {
        return runMode == RunMode.MOD;
    }
    
    /**
     * 是否为独立模式
     * 
     * @return true如果是独立模式
     */
    public boolean isStandaloneMode() {
        return runMode == RunMode.STANDALONE;
    }
    
    /**
     * 检查功能是否在当前模式下可用
     * 
     * @param feature 功能名称
     * @return 是否可用
     */
    public boolean isFeatureAvailable(String feature) {
        switch (feature) {
            case "server_start":
                // 只有独立模式支持启动服务器
                return isStandaloneMode();
            case "server_stop":
                // 两种模式都支持停止服务器
                return true;
            case "minecraft_integration":
                // 只有模组模式支持Minecraft集成
                return isModMode();
            case "discord_commands":
                // 两种模式都支持Discord命令
                return true;
            default:
                return true;
        }
    }
}