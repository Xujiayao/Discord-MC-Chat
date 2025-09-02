package com.xujiayao.discord_mc_chat.common.linking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xujiayao.discord_mc_chat.common.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账户链接管理器
 * 负责处理Discord账户与Minecraft账户的关联
 * 
 * @author Xujiayao
 */
public class AccountLinkingManager {
    
    private static final Logger LOGGER = new Logger();
    private static AccountLinkingManager INSTANCE;
    
    private final ConfigManager configManager;
    private final ObjectMapper objectMapper;
    private final SecureRandom random;
    
    // 存储链接数据和临时链接码
    private final Map<String, AccountLink> minecraftToDiscord = new ConcurrentHashMap<>();
    private final Map<String, AccountLink> discordToMinecraft = new ConcurrentHashMap<>();
    private final Map<String, LinkCode> pendingLinks = new ConcurrentHashMap<>();
    
    private final Path dataFile;
    
    private AccountLinkingManager() {
        this.configManager = ConfigManager.getInstance();
        this.objectMapper = new ObjectMapper();
        this.random = new SecureRandom();
        this.dataFile = Paths.get("config/dmcc/account_links.json");
        
        // 创建数据目录
        try {
            Files.createDirectories(dataFile.getParent());
        } catch (IOException e) {
            LOGGER.error("创建账户链接数据目录失败", e);
        }
        
        loadAccountLinks();
    }
    
    public static AccountLinkingManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AccountLinkingManager();
        }
        return INSTANCE;
    }
    
    /**
     * 生成链接码
     * 
     * @param minecraftUuid Minecraft玩家UUID
     * @param minecraftName Minecraft玩家名称
     * @return 链接码
     */
    public String generateLinkCode(String minecraftUuid, String minecraftName) {
        if (!configManager.getAccountLinkingConfig().isEnable()) {
            throw new IllegalStateException("账户链接功能未启用");
        }
        
        // 检查是否已经链接
        if (isMinecraftLinked(minecraftUuid)) {
            throw new IllegalStateException("该Minecraft账户已经链接到Discord账户");
        }
        
        // 生成6位数字链接码
        String code = String.format("%06d", random.nextInt(1000000));
        
        // 确保链接码唯一
        while (pendingLinks.containsKey(code)) {
            code = String.format("%06d", random.nextInt(1000000));
        }
        
        long expiryMinutes = configManager.getAccountLinkingConfig().getLinkCodeExpiryMinutes();
        long expirationTime = System.currentTimeMillis() + (expiryMinutes * 60 * 1000);
        
        LinkCode linkCode = new LinkCode(code, minecraftUuid, minecraftName, expirationTime);
        pendingLinks.put(code, linkCode);
        
        // 清理过期的链接码
        cleanupExpiredCodes();
        
        LOGGER.info("生成链接码: {} for {}", code, minecraftName);
        return code;
    }
    
    /**
     * 使用链接码链接账户
     * 
     * @param code 链接码
     * @param discordId Discord用户ID
     * @param discordName Discord用户名称
     * @return 是否成功
     */
    public boolean linkAccount(String code, String discordId, String discordName) {
        if (!configManager.getAccountLinkingConfig().isEnable()) {
            throw new IllegalStateException("账户链接功能未启用");
        }
        
        LinkCode linkCode = pendingLinks.get(code);
        if (linkCode == null) {
            LOGGER.warn("无效的链接码: {}", code);
            return false;
        }
        
        if (linkCode.isExpired()) {
            pendingLinks.remove(code);
            LOGGER.warn("链接码已过期: {}", code);
            return false;
        }
        
        // 检查Discord账户是否已经链接
        if (isDiscordLinked(discordId)) {
            LOGGER.warn("Discord账户已经链接: {}", discordId);
            return false;
        }
        
        // 创建链接
        AccountLink link = new AccountLink(
            linkCode.getMinecraftUuid(),
            linkCode.getMinecraftName(),
            discordId,
            discordName
        );
        
        // 保存链接
        minecraftToDiscord.put(linkCode.getMinecraftUuid(), link);
        discordToMinecraft.put(discordId, link);
        
        // 移除临时链接码
        pendingLinks.remove(code);
        
        // 保存到文件
        saveAccountLinks();
        
        LOGGER.info("账户链接成功: {} <-> {}", linkCode.getMinecraftName(), discordName);
        return true;
    }
    
    /**
     * 取消链接
     * 
     * @param minecraftUuid Minecraft UUID
     * @return 是否成功
     */
    public boolean unlinkAccountByMinecraft(String minecraftUuid) {
        AccountLink link = minecraftToDiscord.remove(minecraftUuid);
        if (link != null) {
            discordToMinecraft.remove(link.getDiscordId());
            saveAccountLinks();
            LOGGER.info("取消账户链接: {}", link.getMinecraftName());
            return true;
        }
        return false;
    }
    
    /**
     * 取消链接
     * 
     * @param discordId Discord ID
     * @return 是否成功
     */
    public boolean unlinkAccountByDiscord(String discordId) {
        AccountLink link = discordToMinecraft.remove(discordId);
        if (link != null) {
            minecraftToDiscord.remove(link.getMinecraftUuid());
            saveAccountLinks();
            LOGGER.info("取消账户链接: {}", link.getDiscordName());
            return true;
        }
        return false;
    }
    
    /**
     * 检查Minecraft账户是否已链接
     * 
     * @param minecraftUuid Minecraft UUID
     * @return 是否已链接
     */
    public boolean isMinecraftLinked(String minecraftUuid) {
        return minecraftToDiscord.containsKey(minecraftUuid);
    }
    
    /**
     * 检查Discord账户是否已链接
     * 
     * @param discordId Discord ID
     * @return 是否已链接
     */
    public boolean isDiscordLinked(String discordId) {
        return discordToMinecraft.containsKey(discordId);
    }
    
    /**
     * 通过Minecraft UUID获取链接信息
     * 
     * @param minecraftUuid Minecraft UUID
     * @return 链接信息，未找到返回null
     */
    public AccountLink getLinkByMinecraft(String minecraftUuid) {
        return minecraftToDiscord.get(minecraftUuid);
    }
    
    /**
     * 通过Discord ID获取链接信息
     * 
     * @param discordId Discord ID
     * @return 链接信息，未找到返回null
     */
    public AccountLink getLinkByDiscord(String discordId) {
        return discordToMinecraft.get(discordId);
    }
    
    /**
     * 通过Minecraft名称获取Discord ID
     * 
     * @param minecraftName Minecraft名称
     * @return Discord ID，未找到返回null
     */
    public String getDiscordIdByMinecraftName(String minecraftName) {
        return minecraftToDiscord.values().stream()
            .filter(link -> minecraftName.equals(link.getMinecraftName()))
            .map(AccountLink::getDiscordId)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 清理过期的链接码
     */
    private void cleanupExpiredCodes() {
        pendingLinks.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 加载账户链接数据
     */
    private void loadAccountLinks() {
        if (!Files.exists(dataFile)) {
            LOGGER.info("账户链接数据文件不存在，将使用空数据");
            return;
        }
        
        try {
            Map<String, AccountLink> data = objectMapper.readValue(
                dataFile.toFile(),
                new TypeReference<Map<String, AccountLink>>() {}
            );
            
            minecraftToDiscord.clear();
            discordToMinecraft.clear();
            
            for (AccountLink link : data.values()) {
                minecraftToDiscord.put(link.getMinecraftUuid(), link);
                discordToMinecraft.put(link.getDiscordId(), link);
            }
            
            LOGGER.info("已加载 {} 个账户链接", data.size());
            
        } catch (IOException e) {
            LOGGER.error("加载账户链接数据失败", e);
        }
    }
    
    /**
     * 保存账户链接数据
     */
    private void saveAccountLinks() {
        try {
            Map<String, AccountLink> data = new HashMap<>(minecraftToDiscord);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile.toFile(), data);
            LOGGER.debug("账户链接数据已保存");
        } catch (IOException e) {
            LOGGER.error("保存账户链接数据失败", e);
        }
    }
    
    /**
     * 获取总链接数量
     * 
     * @return 链接数量
     */
    public int getLinkCount() {
        return minecraftToDiscord.size();
    }
    
    /**
     * 获取待处理的链接码数量
     * 
     * @return 链接码数量
     */
    public int getPendingLinkCount() {
        cleanupExpiredCodes();
        return pendingLinks.size();
    }
}