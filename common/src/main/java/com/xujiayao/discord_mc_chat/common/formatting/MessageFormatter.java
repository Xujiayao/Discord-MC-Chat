package com.xujiayao.discord_mc_chat.common.formatting;

import com.xujiayao.discord_mc_chat.common.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.i18n.TranslationService;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息格式化器
 * 负责处理消息模板、占位符替换和格式转换
 * 
 * @author Xujiayao
 */
public class MessageFormatter {
    
    private static final Logger LOGGER = new Logger();
    private static MessageFormatter INSTANCE;
    
    // 正则表达式模式
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final Pattern DISCORD_MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>");
    private static final Pattern DISCORD_CHANNEL_PATTERN = Pattern.compile("<#(\\d+)>");
    private static final Pattern DISCORD_EMOJI_PATTERN = Pattern.compile("<a?:([^:]+):(\\d+)>");
    private static final Pattern MARKDOWN_BOLD_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\*");
    private static final Pattern MARKDOWN_ITALIC_PATTERN = Pattern.compile("\\*(.*?)\\*");
    private static final Pattern MARKDOWN_CODE_PATTERN = Pattern.compile("`(.*?)`");
    private static final Pattern MARKDOWN_STRIKETHROUGH_PATTERN = Pattern.compile("~~(.*?)~~");
    
    private final TranslationService translationService;
    private final ConfigManager configManager;
    
    private MessageFormatter() {
        this.translationService = TranslationService.getInstance();
        this.configManager = ConfigManager.getInstance();
    }
    
    public static MessageFormatter getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MessageFormatter();
        }
        return INSTANCE;
    }
    
    /**
     * 格式化消息模板
     * 
     * @param template 消息模板
     * @param placeholders 占位符映射
     * @return 格式化后的消息
     */
    public String format(String template, Map<String, Object> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        
        String result = template;
        
        // 替换占位符
        if (placeholders != null && !placeholders.isEmpty()) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(result);
            StringBuffer sb = new StringBuffer();
            
            while (matcher.find()) {
                String key = matcher.group(1);
                Object value = placeholders.get(key);
                
                if (value != null) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
                } else {
                    // 保留未找到的占位符
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                }
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        
        return result;
    }
    
    /**
     * 将Discord消息转换为Minecraft格式
     * 
     * @param discordMessage Discord消息内容
     * @return Minecraft格式的消息
     */
    public String discordToMinecraft(String discordMessage) {
        if (discordMessage == null || discordMessage.isEmpty()) {
            return "";
        }
        
        String result = discordMessage;
        
        // 处理Discord提及
        result = DISCORD_MENTION_PATTERN.matcher(result).replaceAll("@用户");
        
        // 处理Discord频道
        result = DISCORD_CHANNEL_PATTERN.matcher(result).replaceAll("#频道");
        
        // 处理Discord表情符号
        result = DISCORD_EMOJI_PATTERN.matcher(result).replaceAll(":$1:");
        
        // 处理Markdown格式
        result = processMarkdownToMinecraft(result);
        
        // 限制换行数量
        int newlineLimit = configManager.getMessagesConfig().getFormatting().getDiscordNewlineLimit();
        if (newlineLimit > 0) {
            result = limitNewlines(result, newlineLimit);
        }
        
        return result.trim();
    }
    
    /**
     * 将Minecraft消息转换为Discord格式
     * 
     * @param minecraftMessage Minecraft消息内容
     * @return Discord格式的消息
     */
    public String minecraftToDiscord(String minecraftMessage) {
        if (minecraftMessage == null || minecraftMessage.isEmpty()) {
            return "";
        }
        
        String result = minecraftMessage;
        
        // 转义Discord特殊字符
        result = escapeDiscordMarkdown(result);
        
        // 处理Minecraft颜色代码（如果需要）
        result = processMinecraftColors(result);
        
        return result.trim();
    }
    
    /**
     * 处理Markdown格式转换为Minecraft格式
     * 
     * @param text 输入文本
     * @return 处理后的文本
     */
    private String processMarkdownToMinecraft(String text) {
        String result = text;
        
        // 粗体 **text** -> §l text §r
        result = MARKDOWN_BOLD_PATTERN.matcher(result).replaceAll("§l$1§r");
        
        // 斜体 *text* -> §o text §r
        result = MARKDOWN_ITALIC_PATTERN.matcher(result).replaceAll("§o$1§r");
        
        // 代码 `text` -> §7 text §r
        result = MARKDOWN_CODE_PATTERN.matcher(result).replaceAll("§7$1§r");
        
        // 删除线 ~~text~~ -> §m text §r
        result = MARKDOWN_STRIKETHROUGH_PATTERN.matcher(result).replaceAll("§m$1§r");
        
        return result;
    }
    
    /**
     * 处理Minecraft颜色代码
     * 
     * @param text 输入文本
     * @return 处理后的文本
     */
    private String processMinecraftColors(String text) {
        // 简单地移除颜色代码，因为Discord不支持
        return text.replaceAll("§[0-9a-fk-or]", "");
    }
    
    /**
     * 转义Discord Markdown字符
     * 
     * @param text 输入文本
     * @return 转义后的文本
     */
    private String escapeDiscordMarkdown(String text) {
        return text.replace("*", "\\*")
                  .replace("_", "\\_")
                  .replace("`", "\\`")
                  .replace("~", "\\~")
                  .replace("|", "\\|")
                  .replace(">", "\\>");
    }
    
    /**
     * 限制换行数量
     * 
     * @param text 输入文本
     * @param limit 换行限制
     * @return 处理后的文本
     */
    private String limitNewlines(String text, int limit) {
        if (limit <= 0) {
            return text;
        }
        
        String[] lines = text.split("\n");
        if (lines.length <= limit) {
            return text;
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                result.append("\n");
            }
            result.append(lines[i]);
        }
        
        if (lines.length > limit) {
            result.append("\n... (").append(lines.length - limit).append(" 行已省略)");
        }
        
        return result.toString();
    }
    
    /**
     * 格式化玩家名称
     * 
     * @param playerName 玩家名称
     * @param useNickname 是否使用昵称
     * @return 格式化后的名称
     */
    public String formatPlayerName(String playerName, boolean useNickname) {
        // TODO: 实现昵称支持
        return playerName;
    }
    
    /**
     * 格式化时间
     * 
     * @param milliseconds 毫秒数
     * @return 格式化后的时间字符串
     */
    public String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + translationService.translate("time.days") + " " + 
                   (hours % 24) + translationService.translate("time.hours");
        } else if (hours > 0) {
            return hours + translationService.translate("time.hours") + " " + 
                   (minutes % 60) + translationService.translate("time.minutes");
        } else if (minutes > 0) {
            return minutes + translationService.translate("time.minutes") + " " + 
                   (seconds % 60) + translationService.translate("time.seconds");
        } else {
            return seconds + translationService.translate("time.seconds");
        }
    }
    
    /**
     * 格式化数字
     * 
     * @param number 数字
     * @return 格式化后的数字字符串
     */
    public String formatNumber(double number) {
        if (number == (long) number) {
            return String.valueOf((long) number);
        } else {
            return String.format("%.2f", number);
        }
    }
}