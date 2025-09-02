package com.xujiayao.discord_mc_chat.common.event;

import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件总线
 * 负责事件的注册、分发和管理
 * 
 * @author Xujiayao
 */
public class EventBus {
    
    private static final Logger LOGGER = new Logger();
    private static EventBus INSTANCE;
    
    private final Map<Class<? extends Event>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();
    
    private EventBus() {}
    
    public static EventBus getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EventBus();
        }
        return INSTANCE;
    }
    
    /**
     * 注册事件监听器
     * 
     * @param eventClass 事件类型
     * @param listener 监听器
     * @param <T> 事件类型
     */
    public <T extends Event> void register(Class<T> eventClass, EventListener<T> listener) {
        listeners.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(listener);
        LOGGER.debug("注册事件监听器: {} -> {}", eventClass.getSimpleName(), listener.getClass().getSimpleName());
    }
    
    /**
     * 取消注册事件监听器
     * 
     * @param eventClass 事件类型
     * @param listener 监听器
     * @param <T> 事件类型
     */
    public <T extends Event> void unregister(Class<T> eventClass, EventListener<T> listener) {
        List<EventListener<?>> eventListeners = listeners.get(eventClass);
        if (eventListeners != null) {
            eventListeners.remove(listener);
            if (eventListeners.isEmpty()) {
                listeners.remove(eventClass);
            }
            LOGGER.debug("取消注册事件监听器: {} -> {}", eventClass.getSimpleName(), listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 发布事件
     * 
     * @param event 事件实例
     * @param <T> 事件类型
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> void post(T event) {
        Class<? extends Event> eventClass = event.getClass();
        List<EventListener<?>> eventListeners = listeners.get(eventClass);
        
        if (eventListeners != null && !eventListeners.isEmpty()) {
            LOGGER.debug("发布事件: {} (监听器数量: {})", eventClass.getSimpleName(), eventListeners.size());
            
            for (EventListener<?> listener : eventListeners) {
                try {
                    if (!event.isCancelled()) {
                        ((EventListener<T>) listener).onEvent(event);
                    }
                } catch (Exception e) {
                    LOGGER.error("事件监听器执行失败: {} -> {}", eventClass.getSimpleName(), listener.getClass().getSimpleName(), e);
                }
            }
        } else {
            LOGGER.debug("没有找到事件监听器: {}", eventClass.getSimpleName());
        }
    }
    
    /**
     * 获取指定事件类型的监听器数量
     * 
     * @param eventClass 事件类型
     * @return 监听器数量
     */
    public int getListenerCount(Class<? extends Event> eventClass) {
        List<EventListener<?>> eventListeners = listeners.get(eventClass);
        return eventListeners != null ? eventListeners.size() : 0;
    }
    
    /**
     * 清空所有事件监听器
     */
    public void clear() {
        listeners.clear();
        LOGGER.info("已清空所有事件监听器");
    }
    
    /**
     * 获取总监听器数量
     * 
     * @return 总监听器数量
     */
    public int getTotalListenerCount() {
        return listeners.values().stream().mapToInt(List::size).sum();
    }
}