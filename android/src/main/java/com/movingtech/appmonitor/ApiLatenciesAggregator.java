package com.movingtech.appmonitor;

import android.content.Context;
import android.util.Log;

import com.movingtech.appmonitor.storage.StorageManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ApiLatenciesAggregator handles collection, buffering, and processing of API latency data
 * Uses a single generic processor for all API latency types with non-blocking queue
 * Configuration is managed by ConfigManager
 */
public class ApiLatenciesAggregator implements ConfigManager.ConfigUpdateListener {

    private static final String TAG = "ApiLatenciesAggregator";

    // Storage and context
    private final StorageManager storageManager;
    private final Context context;
    private final ConfigManager configManager;

    // Configuration - loaded from ConfigManager
    private volatile ConfigManager.ApiLatenciesConfig config;

    // Single executor for all processing
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> processingTask;

    // API latency buffering - using non-blocking queue
    private final ConcurrentLinkedQueue<ApiLatency> latencyQueue;
    private final AtomicInteger currentQueueSize;
    // Temporary overflow queue for when main queue is full
    private final ConcurrentLinkedQueue<ApiLatency> tempQueue;
    private final AtomicInteger tempQueueSize;
    private final AtomicBoolean isProcessing;

    // Statistics (optional)
    private volatile AtomicLong totalLatenciesReceived;
    private volatile AtomicLong totalLatenciesProcessed;
    private volatile AtomicLong totalLatenciesDropped;

    // Session and user context
    private String sessionId;
    private String userId;

    public ApiLatenciesAggregator(Context context, StorageManager storageManager) {
        this.context = context;
        this.storageManager = storageManager;
        this.configManager = ConfigManager.getInstance(context);

        // Load initial configuration
        this.config = configManager.getApiLatenciesConfig();

        // Initialize single executor
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ApiLatenciesAggregator");
            return t;
        });

        // Initialize non-blocking buffering
        this.latencyQueue = new ConcurrentLinkedQueue<>();
        this.currentQueueSize = new AtomicInteger(0);
        this.tempQueue = new ConcurrentLinkedQueue<>();
        this.tempQueueSize = new AtomicInteger(0);
        this.isProcessing = new AtomicBoolean(false);

        // Initialize statistics based on config
        updateStatsConfiguration();

        // Initialize context - sessionId will be set by AppMonitor
        this.userId = "unknown"; // Will be set later

        // Register for config updates
        configManager.setConfigUpdateListener(this);

        Log.i(TAG, "ApiLatenciesAggregator initialized with ConfigManager");
    }

    /**
     * Set user context
     */
    public void setUserContext(String userId) {
        this.userId = userId;
        Log.i(TAG, "User context updated: " + userId);
    }

    /**
     * Set session context (called by AppMonitor)
     */
    public void setSessionContext(String sessionId) {
        this.sessionId = sessionId;
        Log.i(TAG, "Session context updated: " + sessionId);
    }

    /**
     * Add an API latency to be processed
     */
    public boolean addApiLatency(String endpoint, String method, long latency, int statusCode) {
        return addApiLatency(endpoint, method, latency, statusCode, System.currentTimeMillis());
    }

    /**
     * Add an API latency with custom timestamp
     */
    public boolean addApiLatency(String endpoint, String method, long latency, int statusCode, long timestamp) {
        if (endpoint == null || method == null) {
            Log.w(TAG, "Ignoring null endpoint or method");
            return false;
        }

        ApiLatency apiLatency = new ApiLatency(
            sessionId,
            userId,
            timestamp,
            endpoint,
            method,
            latency,
            statusCode,
            getOSInfo()
        );

        // Check queue size limit and use temp queue if needed (no blocking)
        boolean addedToMainQueue = false;
        if (currentQueueSize.get() < config.maxQueueSize) {
            // Main queue has space, add to it
            addedToMainQueue = latencyQueue.offer(apiLatency);
            if (addedToMainQueue) {
                currentQueueSize.incrementAndGet();
            }
        }
        
        if (!addedToMainQueue) {
            // Main queue is full, add to temp queue (no blocking)
            boolean addedToTempQueue = tempQueue.offer(apiLatency);
            if (addedToTempQueue) {
                tempQueueSize.incrementAndGet();
                Log.d(TAG, "Main queue full, added to temp queue: " + method + " " + endpoint + 
                      " (main: " + currentQueueSize.get() + ", temp: " + tempQueueSize.get() + ")");
            } else {
                // This should never happen with ConcurrentLinkedQueue, but handle gracefully
                Log.w(TAG, "Both main and temp queues failed to accept API latency: " + method + " " + endpoint);
                return false;
            }
        } else {
            Log.d(TAG, "Added API latency: " + method + " " + endpoint + " (" + latency + "ms) (queue size: " + currentQueueSize.get() + ")");
        }
        
        if (config.enableStats && totalLatenciesReceived != null) {
            totalLatenciesReceived.incrementAndGet();
        }

        return true; // Always return true since we never drop latencies
    }

    /**
     * Add multiple API latencies at once
     */
    public int addApiLatencies(List<ApiLatency> latencies) {
        int addedCount = 0;
        for (ApiLatency latency : latencies) {
            // Try to add to main queue first
            boolean addedToMainQueue = false;
            if (currentQueueSize.get() < config.maxQueueSize) {
                addedToMainQueue = latencyQueue.offer(latency);
                if (addedToMainQueue) {
                    currentQueueSize.incrementAndGet();
                }
            }
            
            if (!addedToMainQueue) {
                // Main queue is full, add to temp queue
                boolean addedToTempQueue = tempQueue.offer(latency);
                if (addedToTempQueue) {
                    tempQueueSize.incrementAndGet();
                } else {
                    // This should never happen, but log it
                    Log.w(TAG, "Failed to add API latency to both queues during batch operation");
                    continue;
                }
            }
            
            addedCount++;
        }
        
        if (config.enableStats && totalLatenciesReceived != null) {
            totalLatenciesReceived.addAndGet(addedCount);
        }
        
        Log.d(TAG, "Added batch of " + addedCount + " API latencies (queue: " + currentQueueSize.get() + ", temp: " + tempQueueSize.get() + ")");
        return addedCount;
    }

    /**
     * Start processing API latencies
     */
    public boolean startProcessing() {
        if (processingTask != null && !processingTask.isCancelled()) {
            Log.w(TAG, "Processing already started");
            return false;
        }

        processingTask = executor.scheduleWithFixedDelay(
            this::processApiLatenciesBatch,
            config.processIntervalMs,
            config.processIntervalMs,
            TimeUnit.MILLISECONDS
        );

        Log.i(TAG, "API latency processing started with interval: " + config.processIntervalMs + "ms");
        return true;
    }

    /**
     * Stop processing API latencies
     */
    public void stopProcessing() {
        if (processingTask != null) {
            processingTask.cancel(false);
            processingTask = null;
            Log.i(TAG, "API latency processing stopped");
        }
    }

    /**
     * Process API latencies in batch - single generic processor
     */
    private void processApiLatenciesBatch() {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "Processing already in progress, skipping batch");
            return;
        }

        try {
            List<ApiLatency> batch = new ArrayList<>();
            
            // First, drain from main queue up to batch size
            int processed = 0;
            while (processed < config.batchSize && !latencyQueue.isEmpty()) {
                ApiLatency latency = latencyQueue.poll();
                if (latency != null) {
                    batch.add(latency);
                    currentQueueSize.decrementAndGet();
                    processed++;
                }
            }
            
            // If we still have capacity and temp queue has items, process from temp queue
            while (processed < config.batchSize && !tempQueue.isEmpty()) {
                ApiLatency latency = tempQueue.poll();
                if (latency != null) {
                    batch.add(latency);
                    tempQueueSize.decrementAndGet();
                    processed++;
                }
            }
            
            if (!batch.isEmpty()) {
                Log.d(TAG, "Processing batch of " + batch.size() + " API latencies " +
                      "(remaining - main: " + currentQueueSize.get() + ", temp: " + tempQueueSize.get() + ")");
                
                for (ApiLatency latency : batch) {
                    processApiLatency(latency);
                }
                
                if (config.enableStats && totalLatenciesProcessed != null) {
                    totalLatenciesProcessed.addAndGet(batch.size());
                }
                Log.d(TAG, "Processed " + batch.size() + " API latencies");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing API latencies batch", e);
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Generic API latency processor
     */
    private void processApiLatency(ApiLatency latency) {
        try {
            // Store the API latency
            boolean stored = storageManager.storeApiLatency(
                latency.timestamp,
                latency.endpoint,
                latency.method,
                latency.latency,
                latency.statusCode,
                    latency.sessionId,
                    latency.userId
            );

            if (!stored) {
                Log.w(TAG, "Failed to store API latency: " + latency.method + " " + latency.endpoint);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing API latency: " + latency.method + " " + latency.endpoint, e);
        }
    }

    /**
     * Force immediate processing of all queued API latencies
     */
    public void flush() {
        int totalInitialSize = currentQueueSize.get() + tempQueueSize.get();
        Log.i(TAG, "Flushing all queued API latencies (main: " + currentQueueSize.get() + 
              ", temp: " + tempQueueSize.get() + ", total: " + totalInitialSize + ")");
        
        // Process all remaining API latencies from both queues
        while ((!latencyQueue.isEmpty() || !tempQueue.isEmpty()) && 
               (currentQueueSize.get() > 0 || tempQueueSize.get() > 0)) {
            processApiLatenciesBatch();
            
            // Small delay to allow processing to complete
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        Log.i(TAG, "Flush completed, remaining queues - main: " + currentQueueSize.get() + 
              ", temp: " + tempQueueSize.get());
    }

    /**
     * Get processing statistics (only available if stats are enabled)
     */
    public ProcessingStats getStats() {
        if (!config.enableStats) {
            Log.w(TAG, "Statistics are disabled. Enable with enableStats=true in configuration.");
            return new ProcessingStats(0, 0, 0, currentQueueSize.get(), tempQueueSize.get(), false);
        }
        
        return new ProcessingStats(
            totalLatenciesReceived != null ? totalLatenciesReceived.get() : 0,
            totalLatenciesProcessed != null ? totalLatenciesProcessed.get() : 0,
            totalLatenciesDropped != null ? totalLatenciesDropped.get() : 0,
            currentQueueSize.get(),
            tempQueueSize.get(),
            true
        );
    }

    /**
     * Check if statistics are enabled
     */
    public boolean isStatsEnabled() {
        return config.enableStats;
    }

    /**
     * Clean up resources
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down ApiLatenciesAggregator...");
        
        // Stop processing
        stopProcessing();
        
        // Flush remaining data
        flush();
        
        // Shutdown executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                Log.w(TAG, "Executor did not terminate gracefully, forced shutdown");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted while waiting for executor termination");
        }
        
        Log.i(TAG, "ApiLatenciesAggregator shutdown completed");
    }

    private String getOSInfo() {
        return "Android " + android.os.Build.VERSION.RELEASE;
    }

    /**
     * API Latency data class
     */
    public static class ApiLatency {
        public final String sessionId;
        public final String userId;
        public final long timestamp;
        public final String endpoint;
        public final String method;
        public final long latency; // in milliseconds
        public final int statusCode;
        public final String os;

        public ApiLatency(String sessionId, String userId, long timestamp, String endpoint,
                         String method, long latency, int statusCode, String os) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.timestamp = timestamp;
            this.endpoint = endpoint;
            this.method = method;
            this.latency = latency;
            this.statusCode = statusCode;
            this.os = os;
        }

        @Override
        public String toString() {
            return String.format("ApiLatency{%s %s, %dms, status=%d, session=%s}", 
                method, endpoint, latency, statusCode, sessionId);
        }
    }

    /**
     * Processing statistics
     */
    public static class ProcessingStats {
        public final long totalReceived;
        public final long totalProcessed;
        public final long totalDropped;
        public final int queueSize;
        public final int tempQueueSize;
        public final boolean statsEnabled;

        public ProcessingStats(long totalReceived, long totalProcessed, long totalDropped, 
                             int queueSize, int tempQueueSize, boolean statsEnabled) {
            this.totalReceived = totalReceived;
            this.totalProcessed = totalProcessed;
            this.totalDropped = totalDropped;
            this.queueSize = queueSize;
            this.tempQueueSize = tempQueueSize;
            this.statsEnabled = statsEnabled;
        }

        public String getFormattedStats() {
            if (!statsEnabled) {
                return "Statistics disabled";
            }
            
            return String.format("API Latencies Stats - Received: %d, Processed: %d, Dropped: %d, " +
                               "Queue: %d, TempQueue: %d, Success: %.1f%%, Drop: %.1f%%",
                totalReceived, totalProcessed, totalDropped, queueSize, tempQueueSize,
                getSuccessRate(), getDropRate());
        }

        public double getSuccessRate() {
            return totalReceived > 0 ? (totalProcessed * 100.0) / totalReceived : 0.0;
        }

        public double getDropRate() {
            return totalReceived > 0 ? (totalDropped * 100.0) / totalReceived : 0.0;
        }
    }

    @Override
    public void onConfigUpdated(JSONObject newConfig) {
        Log.i(TAG, "Configuration updated, reloading API latencies config");
        
        // Reload configuration
        this.config = configManager.getApiLatenciesConfig();
        
        // Update statistics configuration
        updateStatsConfiguration();
        
        Log.i(TAG, "API latencies configuration reloaded");
    }

    @Override
    public void onConfigError(String error) {
        Log.e(TAG, "Configuration error: " + error);
    }

    private void updateStatsConfiguration() {
        if (config.enableStats) {
            if (totalLatenciesReceived == null) {
                totalLatenciesReceived = new AtomicLong(0);
                totalLatenciesProcessed = new AtomicLong(0);
                totalLatenciesDropped = new AtomicLong(0);
            }
        } else {
            // Disable stats to save memory
            totalLatenciesReceived = null;
            totalLatenciesProcessed = null;
            totalLatenciesDropped = null;
        }
    }
} 