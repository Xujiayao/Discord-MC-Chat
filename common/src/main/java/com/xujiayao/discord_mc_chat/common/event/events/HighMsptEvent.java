package com.xujiayao.discord_mc_chat.common.event.events;

import com.xujiayao.discord_mc_chat.common.event.Event;

/**
 * 高MSPT警告事件
 * 当服务器MSPT超过阈值时触发
 * 
 * @author Xujiayao
 */
public class HighMsptEvent extends Event {
    
    private final double currentMspt;
    private final double threshold;
    private final long duration;
    
    public HighMsptEvent(double currentMspt, double threshold, long duration) {
        this.currentMspt = currentMspt;
        this.threshold = threshold;
        this.duration = duration;
    }
    
    /**
     * 获取当前MSPT值
     * 
     * @return 当前MSPT
     */
    public double getCurrentMspt() {
        return currentMspt;
    }
    
    /**
     * 获取MSPT阈值
     * 
     * @return MSPT阈值
     */
    public double getThreshold() {
        return threshold;
    }
    
    /**
     * 获取持续时间（毫秒）
     * 
     * @return 持续时间
     */
    public long getDuration() {
        return duration;
    }
}