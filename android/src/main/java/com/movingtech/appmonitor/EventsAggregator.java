package com.movingtech.appmonitor;

import android.content.Context;
import android.util.Log;

import com.movingtech.appmonitor.storage.StorageManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EventsAggregator handles collection, buffering, and processing of events
 * Uses a single generic processor for all event types with non-blocking queue
 * Configuration is managed by ConfigManager
 */
public class   EventsAggregator implements ConfigManager.ConfigUpdateListener {

    private static final String TAG = "EventsAggregator";

    // Storage and context
    private final StorageManager storageManager;
    private final Context context;
    private final ConfigManager configManager;

    // Configuration - loaded from ConfigManager
    private volatile ConfigManager.EventsConfig config;

    // Single executor for all processing
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> processingTask;

    // Event buffering - using non-blocking queue
    private final ConcurrentLinkedQueue<Event> eventQueue;
    private final AtomicInteger currentQueueSize;
    // Temporary overflow queue for when main queue is full
    private final ConcurrentLinkedQueue<Event> tempQueue;
    private final AtomicInteger tempQueueSize;
    private final AtomicBoolean isProcessing;

    // Statistics (optional)
    private volatile AtomicLong totalEventsReceived;
    private volatile AtomicLong totalEventsProcessed;
    private volatile AtomicLong totalEventsDropped;

    // Session and user context
    private String sessionId;
    private String userId;

    public EventsAggregator(Context context, StorageManager storageManager) {
        this.context = context;
        this.storageManager = storageManager;
        this.configManager = ConfigManager.getInstance(context);

        // Load initial configuration
        this.config = configManager.getEventsConfig();

        // Initialize single executor
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            return new Thread(r, "EventsAggregator");
        });

        // Initialize non-blocking buffering
        this.eventQueue = new ConcurrentLinkedQueue<>();
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

        Log.i(TAG, "EventsAggregator initialized with ConfigManager");
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
     * Add an event to be processed
     */
    public boolean addEvent(String eventType, String eventName, Map<String, Object> eventPayload) {
        return addEvent(eventType, eventName, eventPayload, new HashMap<>());
    }

    /**
     * Add an event with metadata
     */
    public boolean addEvent(String eventType, String eventName, Map<String, Object> eventPayload, Map<String, Object> metadata) {
        return addEvent(eventType, eventName, eventPayload, metadata, System.currentTimeMillis()); // /1000
    }

    /**
     * Add an event with custom timestamp
     */
    public boolean addEvent(String eventType, String eventName, Map<String, Object> eventPayload, 
                           Map<String, Object> metadata, long timestamp) {
        if (eventType == null || eventName == null || eventPayload == null) {
            Log.w(TAG, "Ignoring null event type, name, or payload");
            return false;
        }

        Event event = new Event(
            sessionId,
            userId,
            timestamp,
            eventType,
            eventName,
            new HashMap<>(eventPayload),
            new HashMap<>(metadata),
            getOSInfo()
        );

        // Check queue size limit and use temp queue if needed (no blocking)
        boolean addedToMainQueue = false;
        if (currentQueueSize.get() < config.maxQueueSize) {
            // Main queue has space, add to it
            addedToMainQueue = eventQueue.offer(event);
            if (addedToMainQueue) {
                currentQueueSize.incrementAndGet();
            }
        }
        
        if (!addedToMainQueue) {
            // Main queue is full, add to temp queue (no blocking)
            boolean addedToTempQueue = tempQueue.offer(event);
            if (addedToTempQueue) {
                tempQueueSize.incrementAndGet();
                Log.d(TAG, "Main queue full, added to temp queue: " + eventType + "." + eventName + 
                      " (main: " + currentQueueSize.get() + ", temp: " + tempQueueSize.get() + ")");
            } else {
                // This should never happen with ConcurrentLinkedQueue, but handle gracefully
                Log.w(TAG, "Both main and temp queues failed to accept event: " + eventType + "." + eventName);
                return false;
            }
        } else {
            Log.d(TAG, "Added event: " + eventType + "." + eventName + " (queue size: " + currentQueueSize.get() + ")");
        }
        
        if (config.enableStats && totalEventsReceived != null) {
            totalEventsReceived.incrementAndGet();
        }

        return true; // Always return true since we never drop events
    }

    /**
     * Add multiple events at once
     */
    public int addEvents(List<Event> events) {
        int addedCount = 0;
        for (Event event : events) {
            // Try to add to main queue first
            boolean addedToMainQueue = false;
            if (currentQueueSize.get() < config.maxQueueSize) {
                addedToMainQueue = eventQueue.offer(event);
                if (addedToMainQueue) {
                    currentQueueSize.incrementAndGet();
                }
            }
            
            if (!addedToMainQueue) {
                // Main queue is full, add to temp queue
                boolean addedToTempQueue = tempQueue.offer(event);
                if (addedToTempQueue) {
                    tempQueueSize.incrementAndGet();
                } else {
                    // This should never happen, but log it
                    Log.w(TAG, "Failed to add event to both queues during batch operation");
                    continue;
                }
            }
            
            if (config.enableStats && totalEventsReceived != null) {
                totalEventsReceived.incrementAndGet();
            }
            addedCount++;
        }
        Log.d(TAG, "Batch added " + addedCount + "/" + events.size() + 
              " events (main: " + currentQueueSize.get() + ", temp: " + tempQueueSize.get() + ")");
        return addedCount;
    }

    /**
     * Start the event processing
     */
    public boolean startProcessing() {
        if (processingTask != null && !processingTask.isCancelled()) {
            Log.w(TAG, "Event processing already started");
            return false;
        }

        processingTask = executor.scheduleWithFixedDelay(
            this::processEventsBatch,
            config.processIntervalMs,
            config.processIntervalMs,
            TimeUnit.MILLISECONDS
        );

        Log.i(TAG, "Event processing started with interval: " + config.processIntervalMs + "ms");
        return true;
    }

    /**
     * Stop the event processing
     */
    public void stopProcessing() {
        if (processingTask != null) {
            processingTask.cancel(false);
            processingTask = null;
        }

        // Process remaining events
        processEventsBatch();

        Log.i(TAG, "Event processing stopped");
    }

    /**
     * Process events in batch - single generic processor
     */
    private void processEventsBatch() {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "Processing already in progress, skipping batch");
            return;
        }

        try {
            List<Event> batch = new ArrayList<>();
            
            // First, drain from main queue up to batch size
            int processed = 0;
            while (processed < config.batchSize && !eventQueue.isEmpty()) {
                Event event = eventQueue.poll();
                if (event != null) {
                    batch.add(event);
                    currentQueueSize.decrementAndGet();
                    processed++;
                }
            }
            
            // If we still have capacity and temp queue has items, process from temp queue
            while (processed < config.batchSize && !tempQueue.isEmpty()) {
                Event event = tempQueue.poll();
                if (event != null) {
                    batch.add(event);
                    tempQueueSize.decrementAndGet();
                    processed++;
                }
            }
            
            if (!batch.isEmpty()) {
                Log.d(TAG, "Processing batch of " + batch.size() + " events " +
                      "(remaining - main: " + currentQueueSize.get() + ", temp: " + tempQueueSize.get() + ")");
                
                for (Event event : batch) {
                    processEvent(event);
                }
                
                if (config.enableStats && totalEventsProcessed != null) {
                    totalEventsProcessed.addAndGet(batch.size());
                }
                Log.d(TAG, "Processed " + batch.size() + " events");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing events batch", e);
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Generic event processor - handles all event types
     */
    private void processEvent(Event event) {
        try {
            // Generic processing: add common metadata if missing
            enrichEventData(event);

            // Create event data map for storage
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("sessionId", event.sessionId);
            eventData.put("userId", event.userId);
            eventData.put("event_name", event.eventName);
            eventData.put("event_payload", event.eventPayload);
            eventData.put("metadata", event.metadata);
            eventData.put("os", event.os);

            // Store the event
            boolean stored = storageManager.storeEvent(
                event.timestamp,
                event.eventType,
                    event.eventName,
                eventData,
                    sessionId,
                    userId
            );

            if (!stored) {
                Log.w(TAG, "Failed to store event: " + event.eventType + "." + event.eventName);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing event: " + event.eventType + "." + event.eventName, e);
        }
    }

    /**
     * Enrich event with common metadata
     */
    private void enrichEventData(Event event) {
        // Add app version if not present
        if (!event.metadata.containsKey("app_version")) {
            event.metadata.put("app_version", getAppVersion());
        }

        // Add device info if not present
        if (!event.metadata.containsKey("device_model")) {
            event.metadata.put("device_model", android.os.Build.MODEL);
            event.metadata.put("api_level", android.os.Build.VERSION.SDK_INT);
        }

        // Add processing timestamp
        event.metadata.put("processed_at", System.currentTimeMillis());
    }

    /**
     * Force immediate processing of all queued events
     */
    public void flush() {
        int totalInitialSize = currentQueueSize.get() + tempQueueSize.get();
        Log.i(TAG, "Flushing all queued events (main: " + currentQueueSize.get() + 
              ", temp: " + tempQueueSize.get() + ", total: " + totalInitialSize + ")");
        
        // Process all remaining events from both queues
        while ((!eventQueue.isEmpty() || !tempQueue.isEmpty()) && 
               (currentQueueSize.get() > 0 || tempQueueSize.get() > 0)) {
            processEventsBatch();
            
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
            return new ProcessingStats(0, 0, 0, currentQueueSize.get(), tempQueueSize.get(), false);
        }
        
        return new ProcessingStats(
            totalEventsReceived != null ? totalEventsReceived.get() : 0,
            totalEventsProcessed != null ? totalEventsProcessed.get() : 0,
            totalEventsDropped != null ? totalEventsDropped.get() : 0,
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
     * Process a single event immediately (bypass queue)
     */
    private void processEventImmediately(Event event) {
        try {
            Log.d(TAG, "Processing event immediately: " + event.eventType + "." + event.eventName);
            
            // Generic processing: add common metadata if missing
            enrichEventData(event);

            // Create event data map for storage
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("sessionId", event.sessionId);
            eventData.put("userId", event.userId);
            eventData.put("event_name", event.eventName);
            eventData.put("event_payload", event.eventPayload);
            eventData.put("metadata", event.metadata);
            eventData.put("os", event.os);

            // Store the event immediately
            boolean stored = storageManager.storeEvent(
                event.timestamp,
                event.eventType,
                    event.eventName,
                eventData,
                    sessionId,
                    userId
            );

            if (stored && config.enableStats && totalEventsProcessed != null) {
                totalEventsProcessed.incrementAndGet();
            } else if (!stored) {
                Log.w(TAG, "Failed to store immediate event: " + event.eventType + "." + event.eventName);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing immediate event: " + event.eventType + "." + event.eventName, e);
        }
    }

    /**
     * Clean up resources
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down EventsAggregator");
        
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
        
        Log.i(TAG, "EventsAggregator shutdown complete");
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
     * Event data structure matching the provided schema
     */
    public static class Event {
        public final String sessionId;
        public final String userId;
        public final long timestamp;
        public final String eventType;
        public final String eventName;
        public final Map<String, Object> eventPayload;
        public final Map<String, Object> metadata;
        public final String os;

        public Event(String sessionId, String userId, long timestamp, String eventType,
                    String eventName, Map<String, Object> eventPayload, 
                    Map<String, Object> metadata, String os) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.timestamp = timestamp;
            this.eventType = eventType;
            this.eventName = eventName;
            this.eventPayload = eventPayload;
            this.metadata = metadata;
            this.os = os;
        }

        @Override
        public String toString() {
            return String.format("Event{type='%s', name='%s', user='%s', session='%s', timestamp=%d}", 
                eventType, eventName, userId, sessionId, timestamp);
        }
    }

    /**
     * Processing statistics (optional)
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
                return "Statistics are disabled. Main queue: " + queueSize + ", Temp queue: " + tempQueueSize;
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("Events Processing Stats:\n");
            sb.append("Received: ").append(totalReceived).append("\n");
            sb.append("Processed: ").append(totalProcessed).append("\n");
            sb.append("Dropped: ").append(totalDropped).append("\n");
            sb.append("Main Queue Size: ").append(queueSize).append("\n");
            sb.append("Temp Queue Size: ").append(tempQueueSize).append("\n");
            sb.append("Total Queue Size: ").append(queueSize + tempQueueSize).append("\n");
            sb.append("Success Rate: ").append(String.format("%.2f%%", getSuccessRate() * 100)).append("\n");
            sb.append("Drop Rate: ").append(String.format("%.2f%%", getDropRate() * 100)).append("\n");
            return sb.toString();
        }

        public double getSuccessRate() {
            if (!statsEnabled || totalReceived == 0) return 1.0;
            return (double) totalProcessed / totalReceived;
        }

        public double getDropRate() {
            if (!statsEnabled || totalReceived == 0) return 0.0;
            return (double) totalDropped / totalReceived;
        }
    }

    @Override
    public void onConfigUpdated(JSONObject newConfig) {
        ConfigManager.EventsConfig newEventsConfig = configManager.getEventsConfig();
        this.config = newEventsConfig;
        updateStatsConfiguration();
        
        // Restart processing with new interval if it's running
        if (processingTask != null && !processingTask.isCancelled()) {
            stopProcessing();
            startProcessing();
        }
        
        Log.i(TAG, "EventsAggregator configuration updated - interval: " + config.processIntervalMs + "ms");
    }

    @Override
    public void onConfigError(String error) {
        Log.e(TAG, "Configuration error: " + error);
        // Continue with current configuration
    }

    private void updateStatsConfiguration() {
        if (config.enableStats) {
            if (totalEventsReceived == null) {
                totalEventsReceived = new AtomicLong(0);
            }
            if (totalEventsProcessed == null) {
                totalEventsProcessed = new AtomicLong(0);
            }
            if (totalEventsDropped == null) {
                totalEventsDropped = new AtomicLong(0);
            }
            Log.i(TAG, "Statistics enabled for EventsAggregator");
        } else {
            totalEventsReceived = null;
            totalEventsProcessed = null;
            totalEventsDropped = null;
            Log.i(TAG, "Statistics disabled for EventsAggregator");
        }
    }
} 