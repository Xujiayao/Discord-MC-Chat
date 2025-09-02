package com.xujiayao.discord_mc_chat.common.event;

/**
 * 事件监听器接口
 * 
 * @author Xujiayao
 */
@FunctionalInterface
public interface EventListener<T extends Event> {
    
    /**
     * 处理事件
     * 
     * @param event 事件实例
     */
    void onEvent(T event);
}