package com.xujiayao.discord_mc_chat.common.i18n;

import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 国际化翻译服务
 * 支持多语言文本翻译和占位符替换
 * 
 * @author Xujiayao
 */
public class TranslationService {
    
    private static final Logger LOGGER = new Logger();
    private static TranslationService INSTANCE;
    
    private final Map<String, Properties> languageProperties = new ConcurrentHashMap<>();
    private String currentLanguage = "zh_cn";
    private Properties fallbackProperties;
    
    private TranslationService() {
        loadLanguage("zh_cn"); // 默认加载中文
        loadLanguage("en_us"); // 加载英文作为后备
        fallbackProperties = languageProperties.get("en_us");
    }
    
    public static TranslationService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TranslationService();
        }
        return INSTANCE;
    }
    
    /**
     * 设置当前语言
     * 
     * @param language 语言代码（如 "zh_cn", "en_us"）
     */
    public void setLanguage(String language) {
        if (!languageProperties.containsKey(language)) {
            loadLanguage(language);
        }
        
        if (languageProperties.containsKey(language)) {
            this.currentLanguage = language;
            LOGGER.info("语言已切换到: {}", language);
        } else {
            LOGGER.warn("语言 {} 不可用，保持当前语言: {}", language, currentLanguage);
        }
    }
    
    /**
     * 获取当前语言
     * 
     * @return 当前语言代码
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * 翻译指定键的文本
     * 
     * @param key 翻译键
     * @return 翻译文本
     */
    public String translate(String key) {
        return translate(key, new Object[0]);
    }
    
    /**
     * 翻译指定键的文本并替换占位符
     * 
     * @param key 翻译键
     * @param args 占位符参数
     * @return 翻译文本
     */
    public String translate(String key, Object... args) {
        String text = getTranslationText(key);
        
        if (args.length == 0) {
            return text;
        }
        
        try {
            return MessageFormat.format(text, args);
        } catch (Exception e) {
            LOGGER.warn("格式化翻译文本失败: {} -> {}", key, text, e);
            return text;
        }
    }
    
    /**
     * 翻译指定键的文本并替换命名占位符
     * 
     * @param key 翻译键
     * @param placeholders 占位符映射
     * @return 翻译文本
     */
    public String translate(String key, Map<String, Object> placeholders) {
        String text = getTranslationText(key);
        
        if (placeholders.isEmpty()) {
            return text;
        }
        
        String result = text;
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = String.valueOf(entry.getValue());
            result = result.replace(placeholder, value);
        }
        
        return result;
    }
    
    /**
     * 获取翻译文本
     * 
     * @param key 翻译键
     * @return 翻译文本
     */
    private String getTranslationText(String key) {
        Properties currentProps = languageProperties.get(currentLanguage);
        
        // 首先在当前语言中查找
        if (currentProps != null && currentProps.containsKey(key)) {
            return currentProps.getProperty(key);
        }
        
        // 然后在后备语言中查找
        if (fallbackProperties != null && fallbackProperties.containsKey(key)) {
            return fallbackProperties.getProperty(key);
        }
        
        // 最后返回键名（表示未找到翻译）
        LOGGER.warn("未找到翻译键: {}", key);
        return key;
    }
    
    /**
     * 加载指定语言文件
     * 
     * @param language 语言代码
     */
    private void loadLanguage(String language) {
        String filename = "/lang/" + language + ".properties";
        
        try (InputStream inputStream = getClass().getResourceAsStream(filename)) {
            if (inputStream == null) {
                LOGGER.warn("语言文件不存在: {}", filename);
                return;
            }
            
            Properties properties = new Properties();
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            
            languageProperties.put(language, properties);
            LOGGER.info("已加载语言文件: {} (条目数: {})", filename, properties.size());
            
        } catch (IOException e) {
            LOGGER.error("加载语言文件失败: {}", filename, e);
        }
    }
    
    /**
     * 重新加载所有语言文件
     */
    public void reloadLanguages() {
        LOGGER.info("重新加载语言文件...");
        languageProperties.clear();
        
        loadLanguage("zh_cn");
        loadLanguage("en_us");
        fallbackProperties = languageProperties.get("en_us");
        
        // 重新设置当前语言以确保其可用
        String oldLanguage = currentLanguage;
        currentLanguage = "en_us"; // 临时设置
        setLanguage(oldLanguage);
        
        LOGGER.info("语言文件重新加载完成");
    }
    
    /**
     * 检查翻译键是否存在
     * 
     * @param key 翻译键
     * @return 是否存在
     */
    public boolean hasTranslation(String key) {
        Properties currentProps = languageProperties.get(currentLanguage);
        return (currentProps != null && currentProps.containsKey(key)) ||
               (fallbackProperties != null && fallbackProperties.containsKey(key));
    }
    
    /**
     * 获取所有可用语言
     * 
     * @return 可用语言列表
     */
    public String[] getAvailableLanguages() {
        return languageProperties.keySet().toArray(new String[0]);
    }
}