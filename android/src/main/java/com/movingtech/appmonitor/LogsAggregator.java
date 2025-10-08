package com.movingtech.appmonitor;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LogsAggregator - Efficient queue-based log collection system
 * Responsibilities:
 * - Accept raw logs from application
 * - Queue logs with timestamps in non-blocking manner
 * - Forward logs to Sessionizer for processing
 * - Maintain simple statistics
 */
public class LogsAggregator implements ConfigManager.ConfigUpdateListener {

    private static final String TAG = "LogsAggregator";

    // Dependencies
    private final Context context;
    private final ConfigManager configManager;
    private Sessionizer sessionizer; // Will be injected

    // Configuration
    private volatile ConfigManager.LogsConfig config;

    // Simple executor for queue processing
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> processingTask;

    // Non-blocking queue for raw logs
    private final ConcurrentLinkedQueue<RawLogEntry> logQueue;
    private final AtomicInteger currentQueueSize;

    // Simple statistics (optional)
    private volatile AtomicLong totalLogsReceived;
    private volatile AtomicLong totalLogsProcessed;

    // State
    private boolean isProcessing = false;

    public LogsAggregator(Context context) {
        this.context = context;
        this.configManager = ConfigManager.getInstance(context);
        this.config = configManager.getLogsConfig();

        // Initialize simple executor
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "LogsAggregator"));

        // Initialize non-blocking queue
        this.logQueue = new ConcurrentLinkedQueue<>();
        this.currentQueueSize = new AtomicInteger(0);

        // Initialize statistics based on config
        updateStatsConfiguration();

        // Register for config updates
        configManager.setConfigUpdateListener(this);

        Log.i(TAG, "LogsAggregator initialized - simple queue-based system");
    }

    /**
     * Set Sessionizer (injected by AppMonitor)
     */
    public void setSessionizer(Sessionizer sessionizer) {
        this.sessionizer = sessionizer;
        Log.i(TAG, "Sessionizer set for LogsAggregator");
    }

    /**
     * Add a simple log entry (most basic method)
     */
    public boolean addLog(String level, String message) {
        return addLog(level, message, null);
    }

    /**
     * Add log entry with tag
     */
    public boolean addLog(String level, String message, String tag) {
        return addLog(level, message, tag, new HashMap<>());
    }

    /**
     * Add log entry with tag and labels
     */
    public boolean addLog(String level, String message, String tag, Map<String, String> labels) {
        return addLog(level, message, tag, labels, new HashMap<>());
    }

    /**
     * Add log entry with all optional parameters
     */
    public boolean addLog(String level, String message, String tag, Map<String, String> labels, Map<String, String> metadata) {
        return addLog(level, message, tag, labels, metadata, System.currentTimeMillis());
    }

    /**
     * Add log entry with custom timestamp - main entry point
     */
    public boolean addLog(String level, String message, String tag, Map<String, String> labels, 
                         Map<String, String> metadata, long timestamp) {
        if (level == null || message == null) {
            Log.w(TAG, "Ignoring null log level or message");
            return false;
        }

        // Create simple raw log entry
        RawLogEntry rawLog = new RawLogEntry(
            level.toUpperCase(),
            message,
            tag,
            new HashMap<>(labels != null ? labels : new HashMap<>()),
            new HashMap<>(metadata != null ? metadata : new HashMap<>()),
            timestamp
        );

        // Non-blocking queue add
        boolean added = false;
        if (currentQueueSize.get() < config.maxQueueSize) {
            added = logQueue.offer(rawLog);
            if (added) {
                currentQueueSize.incrementAndGet();
                if (config.enableStats && totalLogsReceived != null) {
                    totalLogsReceived.incrementAndGet();
                }
            }
        }

        if (!added) {
            Log.w(TAG, "Queue full, dropping log: " + level + " - " + message);
            return false;
        }

        Log.v(TAG, "Queued log: " + level + " - " + message + " (queue size: " + currentQueueSize.get() + ")");
        return true;
    }

    /**
     * Start processing queued logs
     */
    public void startProcessing() {
        if (isProcessing) {
            Log.w(TAG, "LogsAggregator already processing");
            return;
        }

        if (sessionizer == null) {
            Log.e(TAG, "Cannot start processing - Sessionizer not set");
            return;
        }

        processingTask = executor.scheduleWithFixedDelay(
            this::processQueuedLogs,
                config.processIntervalMs, // initial delay to flush
            config.processIntervalMs, // interval
            TimeUnit.MILLISECONDS
        );

        isProcessing = true;
        Log.i(TAG, "LogsAggregator started processing with interval: " + config.processIntervalMs + "ms");
    }

    /**
     * Stop processing
     */
    public void stopProcessing() {
        if (!isProcessing) {
            Log.w(TAG, "LogsAggregator not processing");
            return;
        }

        if (processingTask != null && !processingTask.isCancelled()) {
            processingTask.cancel(false);
            processingTask = null;
        }

        isProcessing = false;
        Log.i(TAG, "LogsAggregator stopped processing");
    }

    /**
     * Process queued logs in batches
     */
    private void processQueuedLogs() {
        if (sessionizer == null) {
            Log.w(TAG, "Sessionizer not available, skipping processing");
            return;
        }

        int batchSize = Math.min(config.batchSize, currentQueueSize.get());
        if (batchSize == 0) {
            return; // No logs to process
        }

        int processedCount = 0;
        for (int i = 0; i < batchSize; i++) {
            RawLogEntry rawLog = logQueue.poll();
            if (rawLog != null) {
                currentQueueSize.decrementAndGet();
                
                // Forward to sessionizer for enrichment and file writing
                sessionizer.processRawLog(rawLog);
                processedCount++;
                
                if (config.enableStats && totalLogsProcessed != null) {
                    totalLogsProcessed.incrementAndGet();
                }
            }
        }

        if (processedCount > 0) {
            Log.d(TAG, "Processed " + processedCount + " logs, remaining queue: " + currentQueueSize.get());
        }
    }

    /**
     * Force flush all queued logs
     */
    public void flush() {
        Log.i(TAG, "Flushing all queued logs (" + currentQueueSize.get() + " logs)");
        
        while (!logQueue.isEmpty() && currentQueueSize.get() > 0) {
            processQueuedLogs();
            
            // Small delay to allow processing
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        Log.i(TAG, "Flush completed, remaining queue: " + currentQueueSize.get());
    }

    /**
     * Get processing statistics (compatibility method for AppMonitor)
     */
    public ProcessingStats getStats() {
        LogsStats stats = getLogsStats();
        return new ProcessingStats(stats.totalReceived, stats.totalProcessed, stats.currentQueueSize, stats.statsEnabled);
    }
    
    /**
     * Get logs statistics
     */
    public LogsStats getLogsStats() {
        if (!config.enableStats) {
            return new LogsStats(0, 0, currentQueueSize.get(), false);
        }

        return new LogsStats(
            totalLogsReceived != null ? totalLogsReceived.get() : 0,
            totalLogsProcessed != null ? totalLogsProcessed.get() : 0,
            currentQueueSize.get(),
            true
        );
    }

    /**
     * Check if processing is running
     */
    public boolean isProcessing() {
        return isProcessing;
    }

    /**
     * Get current queue size
     */
    public int getQueueSize() {
        return currentQueueSize.get();
    }

    /**
     * Shutdown the aggregator
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down LogsAggregator");
        
        stopProcessing();
        flush();
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        Log.i(TAG, "LogsAggregator shutdown complete");
    }

    // ConfigUpdateListener implementation
    @Override
    public void onConfigUpdated(org.json.JSONObject newConfig) {
        ConfigManager.LogsConfig newLogsConfig = configManager.getLogsConfig();
        this.config = newLogsConfig;
        updateStatsConfiguration();
        
        // Restart processing with new interval if running
        if (isProcessing) {
            stopProcessing();
            startProcessing();
        }
        
        Log.i(TAG, "LogsAggregator configuration updated - interval: " + config.processIntervalMs + 
              "ms, batch: " + config.batchSize + ", maxQueue: " + config.maxQueueSize);
    }

    @Override
    public void onConfigError(String error) {
        Log.e(TAG, "Configuration error: " + error);
    }

    private void updateStatsConfiguration() {
        if (config.enableStats) {
            if (totalLogsReceived == null) {
                totalLogsReceived = new AtomicLong(0);
            }
            if (totalLogsProcessed == null) {
                totalLogsProcessed = new AtomicLong(0);
            }
        } else {
            totalLogsReceived = null;
            totalLogsProcessed = null;
        }
    }

    // Inner classes

    /**
     * Simple raw log entry - minimal data before sessionization
     */
    public static class RawLogEntry {
        public final String level;
        public final String message;
        public final String tag;
        public final Map<String, String> labels;
        public final Map<String, String> metadata;
        public final long timestamp;

        public RawLogEntry(String level, String message, String tag, 
                          Map<String, String> labels, Map<String, String> metadata, long timestamp) {
            this.level = level;
            this.message = message;
            this.tag = tag;
            this.labels = labels;
            this.metadata = metadata;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("RawLogEntry{level='%s', message='%s', tag='%s', timestamp=%d}", 
                level, message, tag, timestamp);
        }
    }

    /**
     * Simple processing statistics
     */
    public static class LogsStats {
        public final long totalReceived;
        public final long totalProcessed;
        public final int currentQueueSize;
        public final boolean statsEnabled;

        public LogsStats(long totalReceived, long totalProcessed, int currentQueueSize, boolean statsEnabled) {
            this.totalReceived = totalReceived;
            this.totalProcessed = totalProcessed;
            this.currentQueueSize = currentQueueSize;
            this.statsEnabled = statsEnabled;
        }

        public String getFormattedStats() {
            StringBuilder sb = new StringBuilder();
            sb.append("LogsAggregator Stats:\n");
            if (statsEnabled) {
                sb.append("Total Received: ").append(totalReceived).append("\n");
                sb.append("Total Processed: ").append(totalProcessed).append("\n");
                sb.append("Success Rate: ").append(String.format("%.2f%%", getSuccessRate())).append("\n");
            } else {
                sb.append("Statistics disabled\n");
            }
            sb.append("Current Queue Size: ").append(currentQueueSize).append("\n");
            return sb.toString();
        }

        public double getSuccessRate() {
            return totalReceived > 0 ? (double) totalProcessed / totalReceived * 100.0 : 0.0;
        }
    }

    /**
     * Alias for RawLogEntry to match AppMonitor expectations
     */
    public static class LogEntry extends RawLogEntry {
        public LogEntry(String level, String message, String tag, 
                       Map<String, String> labels, Map<String, String> metadata, long timestamp) {
            super(level, message, tag, labels, metadata, timestamp);
        }
    }
    
    /**
     * Alias for LogsStats to match AppMonitor expectations
     */
    public static class ProcessingStats extends LogsStats {
        public ProcessingStats(long totalReceived, long totalProcessed, int currentQueueSize, boolean statsEnabled) {
            super(totalReceived, totalProcessed, currentQueueSize, statsEnabled);
        }
    }

    /**
     * Set user context (for compatibility with AppMonitor)
     */
    public void setUserContext(String userId) {
        // LogsAggregator doesn't need user context directly
        // This is handled by the Sessionizer
        Log.d(TAG, "User context set: " + userId);
    }
    
    /**
     * Set session context (for compatibility with AppMonitor)
     */
    public void setSessionContext(String sessionId) {
        // LogsAggregator doesn't need session context directly
        // This is handled by the Sessionizer
        Log.d(TAG, "Session context set: " + sessionId);
    }

    /**
     * Batch add logs (compatibility method for AppMonitor)
     */
    public int addLogs(List<LogEntry> logs) {
        if (logs == null || logs.isEmpty()) {
            return 0;
        }

        int addedCount = 0;
        for (LogEntry log : logs) {
            boolean added = addLog(log.level, log.message, log.tag, log.labels, log.metadata, log.timestamp);
            if (added) {
                addedCount++;
            }
        }

        Log.d(TAG, "Batch added " + addedCount + " out of " + logs.size() + " logs");
        return addedCount;
    }
} 