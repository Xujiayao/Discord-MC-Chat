package com.xujiayao.discord_mc_chat.common.monitoring;

import com.xujiayao.discord_mc_chat.common.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.event.EventBus;
import com.xujiayao.discord_mc_chat.common.event.events.HighMsptEvent;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 服务器监控服务
 * 负责监控服务器性能指标如MSPT、TPS等
 * 
 * @author Xujiayao
 */
public class MonitoringService {
    
    private static final Logger LOGGER = new Logger();
    private static MonitoringService INSTANCE;
    
    private final ConfigManager configManager;
    private final EventBus eventBus;
    private final ScheduledExecutorService executorService;
    
    private ScheduledFuture<?> msptMonitorTask;
    private ScheduledFuture<?> channelTopicUpdateTask;
    
    // 性能指标缓存
    private volatile double lastMspt = 0.0;
    private volatile double lastTps = 20.0;
    private volatile int onlinePlayerCount = 0;
    private volatile int maxPlayerCount = 0;
    private volatile long lastHighMsptTime = 0;
    
    private MonitoringService(ScheduledExecutorService executorService) {
        this.configManager = ConfigManager.getInstance();
        this.eventBus = EventBus.getInstance();
        this.executorService = executorService;
    }
    
    public static MonitoringService getInstance() {
        return INSTANCE;
    }
    
    public static void initialize(ScheduledExecutorService executorService) {
        if (INSTANCE == null) {
            INSTANCE = new MonitoringService(executorService);
        }
    }
    
    /**
     * 启动监控服务
     */
    public void start() {
        LOGGER.info("启动服务器监控服务...");
        
        startMsptMonitoring();
        startChannelTopicUpdates();
        
        LOGGER.info("服务器监控服务已启动");
    }
    
    /**
     * 停止监控服务
     */
    public void stop() {
        LOGGER.info("停止服务器监控服务...");
        
        if (msptMonitorTask != null && !msptMonitorTask.isCancelled()) {
            msptMonitorTask.cancel(false);
        }
        
        if (channelTopicUpdateTask != null && !channelTopicUpdateTask.isCancelled()) {
            channelTopicUpdateTask.cancel(false);
        }
        
        LOGGER.info("服务器监控服务已停止");
    }
    
    /**
     * 启动MSPT监控
     */
    private void startMsptMonitoring() {
        if (!configManager.getConfig().getMonitoring().isAnnounceHighMspt()) {
            return;
        }
        
        long interval = configManager.getConfig().getMonitoring().getMsptCheckInterval();
        
        msptMonitorTask = executorService.scheduleAtFixedRate(() -> {
            try {
                checkMspt();
            } catch (Exception e) {
                LOGGER.error("MSPT监控任务执行失败", e);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        
        LOGGER.info("MSPT监控已启动，检查间隔: {}ms", interval);
    }
    
    /**
     * 启动频道主题更新
     */
    private void startChannelTopicUpdates() {
        if (!configManager.getConfig().getMonitoring().isUpdateChannelTopic()) {
            return;
        }
        
        long interval = configManager.getConfig().getMonitoring().getChannelTopicUpdateInterval();
        
        channelTopicUpdateTask = executorService.scheduleAtFixedRate(() -> {
            try {
                updateChannelTopic();
            } catch (Exception e) {
                LOGGER.error("频道主题更新任务执行失败", e);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        
        LOGGER.info("频道主题更新已启动，更新间隔: {}ms", interval);
    }
    
    /**
     * 检查MSPT
     */
    private void checkMspt() {
        double currentMspt = getCurrentMspt();
        double threshold = configManager.getConfig().getMonitoring().getMsptLimit();
        
        if (currentMspt > threshold) {
            long currentTime = System.currentTimeMillis();
            
            // 避免频繁发送警告（至少间隔5分钟）
            if (currentTime - lastHighMsptTime > 300000) {
                long duration = currentTime - lastHighMsptTime;
                
                HighMsptEvent event = new HighMsptEvent(currentMspt, threshold, duration);
                eventBus.post(event);
                
                lastHighMsptTime = currentTime;
                LOGGER.warn("检测到高MSPT: {:.2f}ms (阈值: {:.2f}ms)", currentMspt, threshold);
            }
        }
        
        lastMspt = currentMspt;
    }
    
    /**
     * 更新频道主题
     */
    private void updateChannelTopic() {
        // TODO: 实现Discord频道主题更新
        LOGGER.debug("更新频道主题 - 在线玩家: {}/{}, TPS: {:.1f}, MSPT: {:.2f}ms", 
                    onlinePlayerCount, maxPlayerCount, lastTps, lastMspt);
    }
    
    /**
     * 获取当前MSPT
     * 在模组模式下，这个方法应该被重写以获取真实的MSPT值
     * 
     * @return 当前MSPT值
     */
    protected double getCurrentMspt() {
        // 在独立模式下返回模拟值
        // 在模组模式下应该通过Mixin获取真实值
        return Math.random() * 100; // 模拟值
    }
    
    /**
     * 获取当前TPS
     * 在模组模式下，这个方法应该被重写以获取真实的TPS值
     * 
     * @return 当前TPS值
     */
    protected double getCurrentTps() {
        // 在独立模式下返回模拟值
        // 在模组模式下应该通过Mixin获取真实值
        return 20.0 - (getCurrentMspt() / 50.0); // 模拟值
    }
    
    /**
     * 获取在线玩家数量
     * 在模组模式下，这个方法应该被重写以获取真实的玩家数量
     * 
     * @return 在线玩家数量
     */
    protected int getOnlinePlayerCount() {
        // 在独立模式下返回模拟值
        // 在模组模式下应该获取真实值
        return onlinePlayerCount;
    }
    
    /**
     * 获取最大玩家数量
     * 在模组模式下，这个方法应该被重写以获取真实的最大玩家数量
     * 
     * @return 最大玩家数量
     */
    protected int getMaxPlayerCount() {
        // 在独立模式下返回模拟值
        // 在模组模式下应该获取真实值
        return maxPlayerCount;
    }
    
    /**
     * 更新性能指标
     * 
     * @param mspt MSPT值
     * @param tps TPS值
     */
    public void updatePerformanceMetrics(double mspt, double tps) {
        this.lastMspt = mspt;
        this.lastTps = tps;
    }
    
    /**
     * 更新玩家数量
     * 
     * @param online 在线玩家数量
     * @param max 最大玩家数量
     */
    public void updatePlayerCount(int online, int max) {
        this.onlinePlayerCount = online;
        this.maxPlayerCount = max;
    }
    
    /**
     * 获取服务器状态信息
     * 
     * @return 服务器状态
     */
    public ServerStatus getServerStatus() {
        return new ServerStatus(
            getCurrentTps(),
            getCurrentMspt(),
            getOnlinePlayerCount(),
            getMaxPlayerCount(),
            System.currentTimeMillis()
        );
    }
    
    /**
     * 服务器状态数据类
     */
    public static class ServerStatus {
        private final double tps;
        private final double mspt;
        private final int onlinePlayerCount;
        private final int maxPlayerCount;
        private final long timestamp;
        
        public ServerStatus(double tps, double mspt, int onlinePlayerCount, int maxPlayerCount, long timestamp) {
            this.tps = tps;
            this.mspt = mspt;
            this.onlinePlayerCount = onlinePlayerCount;
            this.maxPlayerCount = maxPlayerCount;
            this.timestamp = timestamp;
        }
        
        public double getTps() { return tps; }
        public double getMspt() { return mspt; }
        public int getOnlinePlayerCount() { return onlinePlayerCount; }
        public int getMaxPlayerCount() { return maxPlayerCount; }
        public long getTimestamp() { return timestamp; }
    }
}