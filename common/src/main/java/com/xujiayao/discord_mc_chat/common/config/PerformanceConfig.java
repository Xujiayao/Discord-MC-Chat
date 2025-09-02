package com.xujiayao.discord_mc_chat.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 性能优化配置类
 * 
 * @author Xujiayao
 */
public class PerformanceConfig {
    
    @JsonProperty("connection_pool_size")
    private int connectionPoolSize = 5;
    
    @JsonProperty("thread_pool")
    private ThreadPoolConfig threadPool = new ThreadPoolConfig();
    
    @JsonProperty("resource_loading")
    private ResourceLoadingConfig resourceLoading = new ResourceLoadingConfig();
    
    @JsonProperty("memory_optimization")
    private MemoryOptimizationConfig memoryOptimization = new MemoryOptimizationConfig();
    
    public int getConnectionPoolSize() { return connectionPoolSize; }
    public void setConnectionPoolSize(int connectionPoolSize) { this.connectionPoolSize = connectionPoolSize; }
    
    public ThreadPoolConfig getThreadPool() { return threadPool; }
    public void setThreadPool(ThreadPoolConfig threadPool) { this.threadPool = threadPool; }
    
    public ResourceLoadingConfig getResourceLoading() { return resourceLoading; }
    public void setResourceLoading(ResourceLoadingConfig resourceLoading) { this.resourceLoading = resourceLoading; }
    
    public MemoryOptimizationConfig getMemoryOptimization() { return memoryOptimization; }
    public void setMemoryOptimization(MemoryOptimizationConfig memoryOptimization) { this.memoryOptimization = memoryOptimization; }
    
    /**
     * 线程池配置
     */
    public static class ThreadPoolConfig {
        @JsonProperty("core_size")
        private int coreSize = 2;
        
        @JsonProperty("max_size")
        private int maxSize = 4;
        
        @JsonProperty("keep_alive_time")
        private long keepAliveTime = 60;
        
        public int getCoreSize() { return coreSize; }
        public void setCoreSize(int coreSize) { this.coreSize = coreSize; }
        
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        
        public long getKeepAliveTime() { return keepAliveTime; }
        public void setKeepAliveTime(long keepAliveTime) { this.keepAliveTime = keepAliveTime; }
    }
    
    /**
     * 资源加载配置
     */
    public static class ResourceLoadingConfig {
        @JsonProperty("lazy_load_assets")
        private boolean lazyLoadAssets = true;
        
        @JsonProperty("cache_discord_users")
        private boolean cacheDiscordUsers = true;
        
        @JsonProperty("cache_expiry_minutes")
        private int cacheExpiryMinutes = 30;
        
        public boolean isLazyLoadAssets() { return lazyLoadAssets; }
        public void setLazyLoadAssets(boolean lazyLoadAssets) { this.lazyLoadAssets = lazyLoadAssets; }
        
        public boolean isCacheDiscordUsers() { return cacheDiscordUsers; }
        public void setCacheDiscordUsers(boolean cacheDiscordUsers) { this.cacheDiscordUsers = cacheDiscordUsers; }
        
        public int getCacheExpiryMinutes() { return cacheExpiryMinutes; }
        public void setCacheExpiryMinutes(int cacheExpiryMinutes) { this.cacheExpiryMinutes = cacheExpiryMinutes; }
    }
    
    /**
     * 内存优化配置
     */
    public static class MemoryOptimizationConfig {
        @JsonProperty("enable_soft_references")
        private boolean enableSoftReferences = true;
        
        @JsonProperty("message_cache_size")
        private int messageCacheSize = 100;
        
        @JsonProperty("avatar_cache_size_mb")
        private int avatarCacheSizeMb = 10;
        
        @JsonProperty("scheduled_gc")
        private boolean scheduledGc = false;
        
        public boolean isEnableSoftReferences() { return enableSoftReferences; }
        public void setEnableSoftReferences(boolean enableSoftReferences) { this.enableSoftReferences = enableSoftReferences; }
        
        public int getMessageCacheSize() { return messageCacheSize; }
        public void setMessageCacheSize(int messageCacheSize) { this.messageCacheSize = messageCacheSize; }
        
        public int getAvatarCacheSizeMb() { return avatarCacheSizeMb; }
        public void setAvatarCacheSizeMb(int avatarCacheSizeMb) { this.avatarCacheSizeMb = avatarCacheSizeMb; }
        
        public boolean isScheduledGc() { return scheduledGc; }
        public void setScheduledGc(boolean scheduledGc) { this.scheduledGc = scheduledGc; }
    }
}