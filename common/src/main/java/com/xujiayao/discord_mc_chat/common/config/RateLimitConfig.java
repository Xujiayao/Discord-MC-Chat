package com.xujiayao.discord_mc_chat.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 限流配置类
 * 
 * @author Xujiayao
 */
public class RateLimitConfig {
    
    @JsonProperty("enable_smart_throttling")
    private boolean enableSmartThrottling = true;
    
    @JsonProperty("priority_queueing")
    private boolean priorityQueueing = true;
    
    @JsonProperty("max_queue_size")
    private int maxQueueSize = 100;
    
    @JsonProperty("batch_size")
    private int batchSize = 5;
    
    @JsonProperty("min_message_interval")
    private long minMessageInterval = 1000;
    
    @JsonProperty("exponential_backoff")
    private boolean exponentialBackoff = true;
    
    @JsonProperty("max_retry_attempts")
    private int maxRetryAttempts = 5;
    
    @JsonProperty("message_aggregation_threshold")
    private int messageAggregationThreshold = 10;
    
    public boolean isEnableSmartThrottling() { return enableSmartThrottling; }
    public void setEnableSmartThrottling(boolean enableSmartThrottling) { this.enableSmartThrottling = enableSmartThrottling; }
    
    public boolean isPriorityQueueing() { return priorityQueueing; }
    public void setPriorityQueueing(boolean priorityQueueing) { this.priorityQueueing = priorityQueueing; }
    
    public int getMaxQueueSize() { return maxQueueSize; }
    public void setMaxQueueSize(int maxQueueSize) { this.maxQueueSize = maxQueueSize; }
    
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    
    public long getMinMessageInterval() { return minMessageInterval; }
    public void setMinMessageInterval(long minMessageInterval) { this.minMessageInterval = minMessageInterval; }
    
    public boolean isExponentialBackoff() { return exponentialBackoff; }
    public void setExponentialBackoff(boolean exponentialBackoff) { this.exponentialBackoff = exponentialBackoff; }
    
    public int getMaxRetryAttempts() { return maxRetryAttempts; }
    public void setMaxRetryAttempts(int maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; }
    
    public int getMessageAggregationThreshold() { return messageAggregationThreshold; }
    public void setMessageAggregationThreshold(int messageAggregationThreshold) { this.messageAggregationThreshold = messageAggregationThreshold; }
}