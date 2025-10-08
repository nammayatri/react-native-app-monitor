package com.movingtech.appmonitor;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.movingtech.appmonitor.storage.StorageManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONObject;

/**
 * MetricsAggregator handles collection, buffering, and processing of metrics
 * Processes numerical data with optional aggregation and stores in database
 * Configuration is managed by ConfigManager
 */
public class MetricsAggregator implements ConfigManager.ConfigUpdateListener {

    private static final String TAG = "MetricsAggregator";

    // Storage and context
    private final StorageManager storageManager;
    private final Context context;
    private final ConfigManager configManager;

    // Configuration - loaded from ConfigManager
    private volatile ConfigManager.MetricsConfig config;

    // Single executor for all processing
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> processingTask;

    // Metrics buffering - using non-blocking queue
    private final ConcurrentLinkedQueue<MetricEntry> metricsQueue;
    private final AtomicInteger currentQueueSize;
    // Temporary overflow queue for when main queue is full
    private final ConcurrentLinkedQueue<MetricEntry> tempQueue;
    private final AtomicInteger tempQueueSize;
    private final AtomicBoolean isProcessing;

    // Statistics (optional)
    private volatile AtomicLong totalMetricsReceived;
    private volatile AtomicLong totalMetricsProcessed;

    // Session and user context
    private String sessionId;
    private String userId;

    public MetricsAggregator(Context context, StorageManager storageManager) {
        this.context = context;
        this.storageManager = storageManager;
        this.configManager = ConfigManager.getInstance(context);

        // Load initial configuration
        this.config = configManager.getMetricsConfig();

        // Initialize single executor
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "MetricsAggregator"));

        // Initialize non-blocking buffering
        this.metricsQueue = new ConcurrentLinkedQueue<>();
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

        Log.i(TAG, "MetricsAggregator initialized with ConfigManager - aggregation: " + config.enableAggregation);
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
     * Add a metric value
     */
    public boolean addMetric(String metricName, Number value) {
        return addMetric(metricName, value, new HashMap<>());
    }

    /**
     * Add a metric with metadata
     */
    public boolean addMetric(String metricName, Number value, Map<String, Object> metadata) {
        return addMetric(metricName, value, metadata, System.currentTimeMillis());
    }

    /**
     * Add a metric with custom timestamp
     */
    public boolean addMetric(String metricName, Number value, Map<String, Object> metadata, long timestamp) {
        if (metricName == null || value == null) {
            Log.w(TAG, "Ignoring null metric name or value");
            return false;
        }

        MetricEntry metricEntry = new MetricEntry(
            sessionId,
            userId,
            timestamp,
            metricName,
            value,
            new HashMap<>(metadata),
            getOSInfo()
        );

        // Check queue size limit and use temp queue if needed (no blocking)
        boolean addedToMainQueue = false;
        if (currentQueueSize.get() < config.maxQueueSize) {
            // Main queue has space, add to it
            addedToMainQueue = metricsQueue.offer(metricEntry);
            if (addedToMainQueue) {
                currentQueueSize.incrementAndGet();
            }
        }
        
        if (!addedToMainQueue) {
            // Main queue is full, add to temp queue (no blocking)
            boolean addedToTempQueue = tempQueue.offer(metricEntry);
            if (addedToTempQueue) {
                tempQueueSize.incrementAndGet();
                Log.d(TAG, "Main queue full, added to temp queue: " + metricName + 
                      " (main: " + currentQueueSize.get() + ", temp: " + tempQueueSize.get() + ")");
            } else {
                // This should never happen with ConcurrentLinkedQueue, but handle gracefully
                Log.w(TAG, "Both main and temp queues failed to accept metric: " + metricName);
                return false;
            }
        } else {
            Log.d(TAG, "Added metric: " + metricName + "=" + value + " (queue size: " + currentQueueSize.get() + ")");
        }
        
        if (config.enableStats && totalMetricsReceived != null) {
            totalMetricsReceived.incrementAndGet();
        }

        return true; // Always return true since we never drop metrics
    }

    /**
     * Add multiple metrics at once
     */
    public int addMetrics(List<MetricEntry> metrics) {
        int addedCount = 0;
        for (MetricEntry metricEntry : metrics) {
            // Try to add to main queue first
            boolean addedToMainQueue = false;
            if (currentQueueSize.get() < config.maxQueueSize) {
                addedToMainQueue = metricsQueue.offer(metricEntry);
                if (addedToMainQueue) {
                    currentQueueSize.incrementAndGet();
                }
            }
            
            if (!addedToMainQueue) {
                // Main queue is full, add to temp queue
                boolean addedToTempQueue = tempQueue.offer(metricEntry);
                if (addedToTempQueue) {
                    tempQueueSize.incrementAndGet();
                } else {
                    // This should never happen, but log it
                    Log.w(TAG, "Failed to add metric to both queues during batch operation");
                    continue;
                }
            }
            
            if (config.enableStats && totalMetricsReceived != null) {
                totalMetricsReceived.incrementAndGet();
            }
            addedCount++;
        }
        Log.d(TAG, "Batch added " + addedCount + "/" + metrics.size() + 
              " metrics (main: " + currentQueueSize.get() + ", temp: " + tempQueueSize.get() + ")");
        return addedCount;
    }

    /**
     * Start the metrics processing
     */
    public void startProcessing() {
        if (processingTask != null && !processingTask.isCancelled()) {
            Log.w(TAG, "Metrics processing already started");
            return;
        }

        processingTask = executor.scheduleWithFixedDelay(
            this::processMetricsBatch,
            config.processIntervalMs,
            config.processIntervalMs,
            TimeUnit.MILLISECONDS
        );

        Log.i(TAG, "Metrics processing started with interval: " + config.processIntervalMs + "ms");
    }

    /**
     * Stop the metrics processing
     */
    public void stopProcessing() {
        if (processingTask != null) {
            processingTask.cancel(false);
            processingTask = null;
        }

        // Process remaining metrics
        processMetricsBatch();

        Log.i(TAG, "Metrics processing stopped");
    }

    /**
     * Process metrics in batch
     */
    private void processMetricsBatch() {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "Processing already in progress, skipping batch");
            return;
        }

        try {
            List<MetricEntry> batch = new ArrayList<>();
            
            // First, drain from main queue up to batch size
            int processed = 0;
            while (processed < config.batchSize && !metricsQueue.isEmpty()) {
                MetricEntry metricEntry = metricsQueue.poll();
                if (metricEntry != null) {
                    batch.add(metricEntry);
                    currentQueueSize.decrementAndGet();
                    processed++;
                }
            }
            
            // If we still have capacity and temp queue has items, process from temp queue
            while (processed < config.batchSize && !tempQueue.isEmpty()) {
                MetricEntry metricEntry = tempQueue.poll();
                if (metricEntry != null) {
                    batch.add(metricEntry);
                    tempQueueSize.decrementAndGet();
                    processed++;
                }
            }
            
            if (!batch.isEmpty()) {
                Log.d(TAG, "Processing batch of " + batch.size() + " metrics " +
                      "(remaining - main: " + currentQueueSize.get() + ", temp: " + tempQueueSize.get() + ")");
                
                if (config.enableAggregation) {
                    // Process with aggregation
                    processAggregatedMetrics(batch);
                } else {
                    // Process individual metrics
                    for (MetricEntry metricEntry : batch) {
                        processMetric(metricEntry);
                    }
                }
                
                if (config.enableStats && totalMetricsProcessed != null) {
                    totalMetricsProcessed.addAndGet(batch.size());
                }
                Log.d(TAG, "Processed " + batch.size() + " metrics");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing metrics batch", e);
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Process a single metric entry
     */
    private void processMetric(MetricEntry metricEntry) {
        try {
            // Enrich metric with additional metadata
            enrichMetricData(metricEntry);

            // Store the metric
            boolean stored = storageManager.storeMetric(
                metricEntry.timestamp,
                metricEntry.metricName,
                metricEntry.value,
                metricEntry.metadata,
                    metricEntry.sessionId,
                    metricEntry.userId
            );

            if (!stored) {
                Log.w(TAG, "Failed to store metric: " + metricEntry.metricName);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing metric: " + metricEntry.metricName, e);
        }
    }

    /**
     * Process metrics with aggregation (group by metric name and time window)
     */
    private void processAggregatedMetrics(List<MetricEntry> batch) {
        Map<String, List<MetricEntry>> groupedMetrics = new HashMap<>();
        
        // Group by metric name
        for (MetricEntry metric : batch) {
            groupedMetrics.computeIfAbsent(metric.metricName, k -> new ArrayList<>()).add(metric);
        }

        // Process each group
        for (Map.Entry<String, List<MetricEntry>> entry : groupedMetrics.entrySet()) {
            String metricName = entry.getKey();
            List<MetricEntry> metrics = entry.getValue();
            
            if (metrics.size() == 1) {
                // Single metric, process normally
                processMetric(metrics.get(0));
            } else {
                // Multiple metrics, aggregate them
                processAggregatedGroup(metricName, metrics);
            }
        }
    }

    /**
     * Aggregate a group of metrics with the same name
     */
    private void processAggregatedGroup(String metricName, List<MetricEntry> metrics) {
        try {
            double sum = 0;
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            long minTimestamp = Long.MAX_VALUE;
            long maxTimestamp = Long.MIN_VALUE;
            
            // Calculate aggregations
            for (MetricEntry metric : metrics) {
                double value = metric.value.doubleValue();
                sum += value;
                min = Math.min(min, value);
                max = Math.max(max, value);
                minTimestamp = Math.min(minTimestamp, metric.timestamp);
                maxTimestamp = Math.max(maxTimestamp, metric.timestamp);
            }
            
            int count = metrics.size();
            double avg = sum / count;
            
            // Create aggregated metadata
            Map<String, Object> aggregatedMetadata = new HashMap<>();
            aggregatedMetadata.put("sessionId", metrics.get(0).sessionId);
            aggregatedMetadata.put("userId", metrics.get(0).userId);
            aggregatedMetadata.put("os", metrics.get(0).os);
            aggregatedMetadata.put("aggregation_type", "batch");
            aggregatedMetadata.put("count", count);
            aggregatedMetadata.put("sum", sum);
            aggregatedMetadata.put("min", min);
            aggregatedMetadata.put("max", max);
            aggregatedMetadata.put("avg", avg);
            aggregatedMetadata.put("time_window_start", minTimestamp);
            aggregatedMetadata.put("time_window_end", maxTimestamp);
            
            // Add common metadata from first metric
            Map<String, Object> originalMetadata = metrics.get(0).metadata;
            enrichMetadata(aggregatedMetadata, originalMetadata);

            // Store aggregated metric using average value and middle timestamp
            long middleTimestamp = (minTimestamp + maxTimestamp) / 2;
            boolean stored = storageManager.storeMetric(
                middleTimestamp,
                metricName + "_aggregated",
                avg,
                aggregatedMetadata,
                    sessionId,
                    userId
            );

            if (!stored) {
                Log.w(TAG, "Failed to store aggregated metric: " + metricName);
            } else {
                Log.d(TAG, "Stored aggregated metric: " + metricName + 
                     " (count=" + count + ", avg=" + String.format("%.2f", avg) + ")");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing aggregated group: " + metricName, e);
        }
    }

    /**
     * Enrich metric with common metadata
     */
    private void enrichMetricData(MetricEntry metricEntry) {
        enrichMetadata(metricEntry.metadata, null);
    }

    /**
     * Common metadata enrichment
     */
    private void enrichMetadata(Map<String, Object> metadata, Map<String, Object> originalMetadata) {
        // Add app version if not present
        if (!metadata.containsKey("app_version")) {
            metadata.put("app_version", getAppVersion());
        }

        // Add device info if not present
        if (!metadata.containsKey("device_model")) {
            metadata.put("device_model", android.os.Build.MODEL);
            metadata.put("api_level", android.os.Build.VERSION.SDK_INT);
        }

        // Add processing timestamp
        metadata.put("processed_at", System.currentTimeMillis());
        
        // Merge original metadata if provided
        if (originalMetadata != null) {
            for (Map.Entry<String, Object> entry : originalMetadata.entrySet()) {
                if (!metadata.containsKey(entry.getKey())) {
                    metadata.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * Process a single metric immediately (bypass queue)
     */
    private void processMetricImmediately(MetricEntry metricEntry) {
        try {
            Log.d(TAG, "Processing metric immediately: " + metricEntry.metricName);
            
            enrichMetricData(metricEntry);
            
            boolean stored = storageManager.storeMetric(
                metricEntry.timestamp,
                metricEntry.metricName,
                metricEntry.value,
                metricEntry.metadata,
                    metricEntry.sessionId,
                    metricEntry.userId
            );
            
            if (stored && config.enableStats && totalMetricsProcessed != null) {
                totalMetricsProcessed.incrementAndGet();
            } else if (!stored) {
                Log.w(TAG, "Failed to store immediate metric: " + metricEntry.metricName);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing immediate metric: " + metricEntry.metricName, e);
        }
    }

    /**
     * Force immediate processing of all queued metrics
     */
    public void flush() {
        int totalInitialSize = currentQueueSize.get() + tempQueueSize.get();
        Log.i(TAG, "Flushing all queued metrics (main: " + currentQueueSize.get() + 
              ", temp: " + tempQueueSize.get() + ", total: " + totalInitialSize + ")");
        
        // Process all remaining metrics from both queues
        while ((!metricsQueue.isEmpty() || !tempQueue.isEmpty()) && 
               (currentQueueSize.get() > 0 || tempQueueSize.get() > 0)) {
            processMetricsBatch();
            
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
            Log.w(TAG, "Statistics are disabled. Enable with enableStats=true in constructor.");
            return new ProcessingStats(0, 0, currentQueueSize.get(), tempQueueSize.get(), false);
        }
        
        return new ProcessingStats(
            totalMetricsReceived != null ? totalMetricsReceived.get() : 0,
            totalMetricsProcessed != null ? totalMetricsProcessed.get() : 0,
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
     * Check if aggregation is enabled
     */
    public boolean isAggregationEnabled() {
        return config.enableAggregation;
    }

    /**
     * Clean up resources
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down MetricsAggregator");
        
        stopProcessing();
        flush();
        
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        Log.i(TAG, "MetricsAggregator shutdown complete");
    }

    // Helper methods
    private String getOSInfo() {
        return "Android " + android.os.Build.VERSION.RELEASE;
    }

    private String getAppVersion() {
        try {
            return context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0)
                .versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    // Inner classes

    /**
     * Metric entry data structure
     */
    public static class MetricEntry {
        public final String sessionId;
        public final String userId;
        public final long timestamp;
        public final String metricName;
        public final Number value;
        public final Map<String, Object> metadata;
        public final String os;

        public MetricEntry(String sessionId, String userId, long timestamp, String metricName,
                          Number value, Map<String, Object> metadata, String os) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.timestamp = timestamp;
            this.metricName = metricName;
            this.value = value;
            this.metadata = metadata;
            this.os = os;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ENGLISH,"MetricEntry{name='%s', value=%s, user='%s', session='%s', timestamp=%d}",
                metricName, value, userId, sessionId, timestamp);
        }
    }

    /**
     * Processing statistics (optional)
     */
    public static class ProcessingStats {
        public final long totalReceived;
        public final long totalProcessed;
        public final int queueSize;
        public final int tempQueueSize;
        public final boolean statsEnabled;

        public ProcessingStats(long totalReceived, long totalProcessed, int queueSize, int tempQueueSize, boolean statsEnabled) {
            this.totalReceived = totalReceived;
            this.totalProcessed = totalProcessed;
            this.queueSize = queueSize;
            this.tempQueueSize = tempQueueSize;
            this.statsEnabled = statsEnabled;
        }

        public String getFormattedStats() {
            if (!statsEnabled) {
                return "Statistics are disabled. Main queue: " + queueSize + ", Temp queue: " + tempQueueSize;
            }

            return "Metrics Processing Stats:\n" +
                    "Received: " + totalReceived + "\n" +
                    "Processed: " + totalProcessed + "\n" +
                    "Main Queue Size: " + queueSize + "\n" +
                    "Temp Queue Size: " + tempQueueSize + "\n" +
                    "Total Queue Size: " + (queueSize + tempQueueSize) + "\n" +
                    "Success Rate: " + String.format(Locale.ENGLISH, "%.2f%%", getSuccessRate() * 100) + "\n";
        }

        public double getSuccessRate() {
            if (!statsEnabled || totalReceived == 0) return 1.0;
            return (double) totalProcessed / totalReceived;
        }
    }

    @Override
    public void onConfigUpdated(JSONObject newConfig) {
        this.config = configManager.getMetricsConfig();
        updateStatsConfiguration();
        
        // Restart processing with new interval if it's running
        if (processingTask != null && !processingTask.isCancelled()) {
            stopProcessing();
            startProcessing();
        }
        
        Log.i(TAG, "MetricsAggregator configuration updated - aggregation: " + config.enableAggregation + 
              ", interval: " + config.processIntervalMs + "ms");
    }

    @Override
    public void onConfigError(String error) {
        Log.e(TAG, "Configuration error: " + error);
        // Continue with current configuration
    }

    private void updateStatsConfiguration() {
        if (config.enableStats) {
            if (totalMetricsReceived == null) {
                totalMetricsReceived = new AtomicLong(0);
            }
            if (totalMetricsProcessed == null) {
                totalMetricsProcessed = new AtomicLong(0);
            }
            Log.i(TAG, "Statistics enabled for MetricsAggregator");
        } else {
            totalMetricsReceived = null;
            totalMetricsProcessed = null;
            Log.i(TAG, "Statistics disabled for MetricsAggregator");
        }
    }
} 