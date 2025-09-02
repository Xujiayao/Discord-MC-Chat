package com.xujiayao.discord_mc_chat.common.event;

/**
 * 事件基类
 * 所有DMCC事件都应该继承这个类
 * 
 * @author Xujiayao
 */
public abstract class Event {
    
    private boolean cancelled = false;
    private final long timestamp;
    
    public Event() {
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 获取事件发生时间戳
     * 
     * @return 时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 检查事件是否被取消
     * 
     * @return 是否被取消
     */
    public boolean isCancelled() {
        return cancelled;
    }
    
    /**
     * 设置事件取消状态
     * 
     * @param cancelled 是否取消
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}