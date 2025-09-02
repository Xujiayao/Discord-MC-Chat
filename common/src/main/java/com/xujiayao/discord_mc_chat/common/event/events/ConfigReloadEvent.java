package com.xujiayao.discord_mc_chat.common.event.events;

import com.xujiayao.discord_mc_chat.common.event.Event;

/**
 * 配置重载事件
 * 当配置文件被重新加载时触发
 * 
 * @author Xujiayao
 */
public class ConfigReloadEvent extends Event {
    
    private final boolean success;
    private final String errorMessage;
    
    public ConfigReloadEvent(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }
    
    public ConfigReloadEvent(boolean success) {
        this(success, null);
    }
    
    /**
     * 获取重载是否成功
     * 
     * @return 是否成功
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 获取错误消息（如果有）
     * 
     * @return 错误消息，成功时为null
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}