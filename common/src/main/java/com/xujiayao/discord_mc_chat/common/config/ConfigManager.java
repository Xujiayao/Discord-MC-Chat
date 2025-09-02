package com.xujiayao.discord_mc_chat.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 配置管理器
 * 负责加载、保存和验证配置文件
 * 
 * @author Xujiayao
 */
public class ConfigManager {
    
    private static final Logger LOGGER = new Logger();
    private static final String CONFIG_DIR = "config/dmcc";
    
    private static ConfigManager INSTANCE;
    private final ObjectMapper yamlMapper;
    private final Path configPath;
    
    private Config config;
    private WebhookConfig webhookConfig;
    private MessagesConfig messagesConfig;
    private SecurityConfig securityConfig;
    private RichPresenceConfig richPresenceConfig;
    private RateLimitConfig rateLimitConfig;
    private PerformanceConfig performanceConfig;
    private AccountLinkingConfig accountLinkingConfig;
    
    private ConfigManager() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR));
        this.configPath = Paths.get(CONFIG_DIR);
        
        try {
            // 创建配置目录
            Files.createDirectories(configPath);
        } catch (IOException e) {
            LOGGER.error("创建配置目录失败: {}", e.getMessage());
        }
    }
    
    public static ConfigManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ConfigManager();
        }
        return INSTANCE;
    }
    
    /**
     * 加载所有配置文件
     */
    public void loadConfigs() {
        LOGGER.info("开始加载配置文件...");
        
        config = loadConfig("config.yml", Config.class, new Config());
        webhookConfig = loadConfig("webhook.yml", WebhookConfig.class, new WebhookConfig());
        messagesConfig = loadConfig("messages.yml", MessagesConfig.class, new MessagesConfig());
        securityConfig = loadConfig("security.yml", SecurityConfig.class, new SecurityConfig());
        richPresenceConfig = loadConfig("rich-presence.yml", RichPresenceConfig.class, new RichPresenceConfig());
        rateLimitConfig = loadConfig("rate-limit.yml", RateLimitConfig.class, new RateLimitConfig());
        performanceConfig = loadConfig("performance.yml", PerformanceConfig.class, new PerformanceConfig());
        accountLinkingConfig = loadConfig("account-linking.yml", AccountLinkingConfig.class, new AccountLinkingConfig());
        
        validateConfigs();
        LOGGER.info("配置文件加载完成");
    }
    
    /**
     * 加载单个配置文件
     * 
     * @param filename 文件名
     * @param clazz 配置类
     * @param defaultConfig 默认配置
     * @return 配置对象
     */
    private <T> T loadConfig(String filename, Class<T> clazz, T defaultConfig) {
        Path configFile = configPath.resolve(filename);
        
        try {
            if (!Files.exists(configFile)) {
                LOGGER.info("配置文件 {} 不存在，创建默认配置", filename);
                saveConfig(filename, defaultConfig);
                return defaultConfig;
            }
            
            T config = yamlMapper.readValue(configFile.toFile(), clazz);
            LOGGER.info("成功加载配置文件: {}", filename);
            return config;
            
        } catch (IOException e) {
            LOGGER.error("加载配置文件 {} 失败: {}", filename, e.getMessage());
            LOGGER.warn("使用默认配置");
            return defaultConfig;
        }
    }
    
    /**
     * 保存配置文件
     * 
     * @param filename 文件名
     * @param config 配置对象
     */
    private <T> void saveConfig(String filename, T config) {
        Path configFile = configPath.resolve(filename);
        
        try {
            yamlMapper.writeValue(configFile.toFile(), config);
            LOGGER.info("配置文件 {} 保存成功", filename);
        } catch (IOException e) {
            LOGGER.error("保存配置文件 {} 失败: {}", filename, e.getMessage());
        }
    }
    
    /**
     * 重新加载配置文件
     */
    public void reloadConfigs() {
        LOGGER.info("重新加载配置文件...");
        loadConfigs();
        LOGGER.info("配置文件重新加载完成");
    }
    
    /**
     * 验证配置有效性
     */
    private void validateConfigs() {
        // 验证主配置
        if (config.getBotToken().isEmpty()) {
            LOGGER.warn("机器人令牌为空，请在配置文件中设置");
        }
        
        if (config.getChannels().getMain().isEmpty()) {
            LOGGER.warn("主频道ID为空，请在配置文件中设置");
        }
        
        // 验证监控配置
        if (config.getMonitoring().getMsptLimit() <= 0) {
            LOGGER.warn("MSPT限制值无效，使用默认值50.0");
            config.getMonitoring().setMsptLimit(50.0);
        }
        
        // 验证安全配置
        if (securityConfig.getAdminsIds().isEmpty()) {
            LOGGER.warn("管理员ID列表为空，某些命令将无法使用");
        }
        
        LOGGER.info("配置验证完成");
    }
    
    // Getters
    public Config getConfig() { return config; }
    public WebhookConfig getWebhookConfig() { return webhookConfig; }
    public MessagesConfig getMessagesConfig() { return messagesConfig; }
    public SecurityConfig getSecurityConfig() { return securityConfig; }
    public RichPresenceConfig getRichPresenceConfig() { return richPresenceConfig; }
    public RateLimitConfig getRateLimitConfig() { return rateLimitConfig; }
    public PerformanceConfig getPerformanceConfig() { return performanceConfig; }
    public AccountLinkingConfig getAccountLinkingConfig() { return accountLinkingConfig; }
}