package com.movingtech.appmonitor;

import android.content.Context;
import android.util.Log;

import com.movingtech.appmonitor.storage.FileStorageProvider;
import com.movingtech.appmonitor.storage.StorageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;

/**
 * DataFlusher handles periodic transmission of stored data to backend API endpoints
 * Reads data from StorageManager and sends to /ingest/metrics, /ingest/events, /ingest/logs
 * Configuration is managed by ConfigManager
 */
public class DataFlusher implements ConfigManager.ConfigUpdateListener {

    private static final String TAG = "DataFlusher";
    
    // API endpoints
    private static final String METRICS_ENDPOINT = "/ingest/metrics";
    private static final String EVENTS_ENDPOINT = "/ingest/events";
    private static final String API_LATENCIES_ENDPOINT = "/ingest/api-latencies";
    private static final String LOGS_ENDPOINT = "/ingest/logs";
    
    // HTTP configuration
    private static final int CONNECT_TIMEOUT_MS = 15000; // 15 seconds
    private static final int READ_TIMEOUT_MS = 30000; // 30 seconds
    private static final String CONTENT_TYPE = "application/json";
    
    // Storage and context
    private final Context context;
    private final StorageManager storageManager;
    private final ConfigManager configManager;
    
    // Configuration - loaded from ConfigManager
    private volatile ConfigManager.DataFlusherConfig config;
    
    // Network configuration
    private String baseApiUrl;
    private String apiKey;
    
    // Executor for periodic flushing
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> flushTask;
    
    // State management
    private final AtomicBoolean isRunning;
    private final AtomicBoolean isFlushing;
    
    // Statistics (optional)
    private volatile AtomicLong totalFlushAttempts;
    private volatile AtomicLong totalFlushSuccesses;
    private volatile AtomicLong totalFlushFailures;
    private volatile AtomicLong totalRecordsSent;
    
    // Session and user context
    private String sessionId;
    private String userId;
    
    public DataFlusher(Context context, StorageManager storageManager) {
        this.context = context;
        this.storageManager = storageManager;
        this.configManager = ConfigManager.getInstance(context);
        
        // Load initial configuration
        this.config = configManager.getDataFlusherConfig();
        
        // Initialize executor
        this.executor = Executors.newScheduledThreadPool(3,r -> new Thread(r, "DataFlusher"));
        
        // Initialize state
        this.isRunning = new AtomicBoolean(false);
        this.isFlushing = new AtomicBoolean(false);
        
        // Initialize statistics based on config
        updateStatsConfiguration();
        
        // Initialize context - sessionId will be set by AppMonitor
//        this.userId = "unknown"; // Will be set later
        
        // Register for config updates
        configManager.setConfigUpdateListener(this);
        
        Log.i(TAG, "DataFlusher initialized with ConfigManager");
    }
    
    /**
     * Initialize with API configuration
     */
    public void initialize(String baseApiUrl, String apiKey) {
        this.baseApiUrl = baseApiUrl.endsWith("/") ? baseApiUrl.substring(0, baseApiUrl.length() - 1) : baseApiUrl;
        this.apiKey = apiKey;
        
        Log.i(TAG, "DataFlusher initialized with API URL: " + this.baseApiUrl);
    }
    
    /**
     * Set user context
     */
    public void setUserContext(String userId) {
        this.userId = userId;
        Log.i(TAG, "User context updated: " + userId);
    }
    
    /**
     * Set session context
     */
    public void setSessionContext(String sessionId) {
        this.sessionId = sessionId;
        Log.i(TAG, "Session context updated: " + sessionId);
    }
    
    /**
     * Start periodic data flushing
     */
    public void startFlushing() {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "Data flushing already running");
            return;
        }
        
        if (baseApiUrl == null) {
            Log.e(TAG, "Cannot start flushing - base API URL not configured");
            isRunning.set(false);
            return;
        }
        
        if (flushTask != null) {
            flushTask.cancel(false);
        }
        
        flushTask = executor.scheduleWithFixedDelay(
            this::performFlush,
            config.initialDelayMs,
            config.flushIntervalMs,
            TimeUnit.MILLISECONDS
        );
        
        Log.i(TAG, "Data flushing started with interval: " + config.flushIntervalMs + "ms");
    }
    
    /**
     * Stop periodic data flushing
     */
    public void stopFlushing() {
        isRunning.set(false);
        
        if (flushTask != null) {
            flushTask.cancel(false);
            flushTask = null;
        }
        
        Log.i(TAG, "Data flushing stopped");
    }
    
    /**
     * Perform immediate flush of all stored data
     */
    public void flushNow() {
        if (!isFlushing.compareAndSet(false, true)) {
            Log.d(TAG, "Flush already in progress, skipping immediate flush");
            return;
        }
        
        try {
            Log.i(TAG, "Performing immediate data flush");
            performFlushOperation();
        } finally {
            isFlushing.set(false);
        }
    }
    
    /**
     * Periodic flush operation
     */
    private void performFlush() {
        if (!isRunning.get()) {
            return;
        }
        
        if (!isFlushing.compareAndSet(false, true)) {
            Log.d(TAG, "Previous flush still in progress, skipping this cycle");
            return;
        }
        
        try {
            if (config.enableStats && totalFlushAttempts != null) {
                totalFlushAttempts.incrementAndGet();
            }
            
            performFlushOperation();
            
            if (config.enableStats && totalFlushSuccesses != null) {
                totalFlushSuccesses.incrementAndGet();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during periodic flush", e);
            if (config.enableStats && totalFlushFailures != null) {
                totalFlushFailures.incrementAndGet();
            }
        } finally {
            isFlushing.set(false);
        }
    }
    
    /**
     * Core flush operation - sends all types of data
     */
    private void performFlushOperation() {
        if (!config.enableMetricsFlush && !config.enableEventsFlush && !config.enableLogsFlush) {
            Log.d(TAG, "All data types disabled in configuration, skipping flush");
            return;
        }
        
        boolean hasData = false;
        
        // Flush metrics
        if (config.enableMetricsFlush) {
            // Get metrics from storage (based only on batch size)
            List<Map<String, Object>> metrics = storageManager.getMetrics(config.batchSize);
            List<Map<String, Object>> apiLatencies = storageManager.getApiLatencies(config.batchSize);
            hasData |= flushMetrics(metrics, apiLatencies, true);
        }
        
        // Flush events  
        if (config.enableEventsFlush) {
            // Get events from storage (based only on batch size)
            List<Map<String, Object>> events = storageManager.getEvents(config.batchSize);
            hasData |= flushEvents(events, true);
        }
        
        // Flush logs
        if (config.enableLogsFlush) {
            // Get logs configuration for whitelisted levels
            ConfigManager.LogsConfig logsConfig = configManager.getLogsConfig();
            hasData |= flushLogs(logsConfig, true);
        }
        
        if (!hasData) {
            Log.d(TAG, "No data to flush");
        }
    }


    private JSONArray getLatenciesArray(List<Map<String, Object>> apiLatencies){
        JSONArray latenciesArray = new JSONArray();
        try{
            for (Map<String, Object> apiLatency : apiLatencies) {
                JSONObject payload = new JSONObject();
                payload.put("sessionId", apiLatency.get("session_id"));
                payload.put("userId", apiLatency.get("user_id"));
                payload.put("timestamp", apiLatency.get("timestamp"));
                payload.put("endpoint", apiLatency.get("endpoint"));
                payload.put("method", apiLatency.get("method"));
                payload.put("latency", apiLatency.get("latency"));
                payload.put("statusCode", apiLatency.get("statusCode"));
                payload.put("os", getOSInfo());
                latenciesArray.put(payload);
            }
        }catch (Exception e){
            Log.e(TAG, "Error getting API latencies", e);
        }
        return latenciesArray;
    }

    private JSONArray getMetricsArray(List<Map<String, Object>> metrics){
        JSONArray metricsArray = new JSONArray();
        try{
            // Define required metric fields to ensure consistent schema
            String[] requiredMetricFields = {"cpuUsage", "memoryUsage", "temperature", "battery"};

            // Transform metrics: Group by timestamp
            Map<Long, Map<String, Object>> groupedMetrics = new HashMap<>();

            for (Map<String, Object> metric : metrics) {
                Long timestamp = (Long) metric.get("timestamp");
                String metricName = (String) metric.get("name");
                Object metricValue = metric.get("value");

                // Get or create the group for this timestamp
                Map<String, Object> timestampGroup = groupedMetrics.get(timestamp);
                if (timestampGroup == null) {
                    timestampGroup = new HashMap<>();
                    timestampGroup.put("timestamp", timestamp);
                    groupedMetrics.put(timestamp, timestampGroup);
                }

                // Add the metric to the group
                timestampGroup.put(metricName, metricValue);
            }

            // Ensure all groups have all required fields (set missing ones to null)
            for (Map<String, Object> group : groupedMetrics.values()) {
                for (String requiredField : requiredMetricFields) {
                    if (!group.containsKey(requiredField)) {
                        group.put(requiredField, null);
                    }
                }
            }

            for (Map<String, Object> group : groupedMetrics.values()) {
                JSONObject groupJson = new JSONObject();

                // Add all fields from the group to the JSON object
                for (Map.Entry<String, Object> entry : group.entrySet()) {
                    groupJson.put(entry.getKey(), entry.getValue());
                }
                metricsArray.put(groupJson);
            }

        }catch (Exception e){
            Log.e(TAG, "Error getting metrics", e);
        }
        return metricsArray;
    }
    
    /**
     * Flush metrics data to backend
     */
    private boolean flushMetrics(List<Map<String, Object>> metrics, List<Map<String, Object>> apiLatencies, boolean keepOnFailure) {
        try {
            // Convert grouped metrics to JSONArray
            JSONArray metricsArray = getMetricsArray(metrics);
            JSONArray latenciesArray = getLatenciesArray(apiLatencies);

            String sessionId;
            String userId;

            if (!apiLatencies.isEmpty()){
                sessionId = (String) apiLatencies.get(0).get("session_id");
                userId = (String) apiLatencies.get(0).get("user_id");
            } else if (!metrics.isEmpty()) {
                sessionId = (String) metrics.get(0).get("session_id");
                userId = (String) metrics.get(0).get("user_id");
            }else {
                sessionId= this.sessionId;
                userId = this.userId;
            }


            // Build request payload according to API schema
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("userId", userId);
            payload.put("os", getOSInfo());
            payload.put("metrics", metricsArray);
            payload.put("latencies", latenciesArray);

            // Send to backend
            boolean success = sendDataToBackend(METRICS_ENDPOINT, payload);
            
            if (success || !keepOnFailure) {
                // Delete only the specific metrics that were fetched
                storageManager.deleteMetrics(metrics);
                storageManager.deleteApiLatencies(apiLatencies);
                
                if (config.enableStats && totalRecordsSent != null) {
                    totalRecordsSent.addAndGet(metrics.size());
                }
                
                Log.i(TAG, "Successfully flushed metrics");
            }
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error flushing metrics", e);
            return false;
        }
    }
    
    /**
     * Flush events data to backend
     */
    private boolean flushEvents(List<Map<String, Object>> events, boolean keepOnFailure) {
        try {
            if (events.isEmpty()) {
                Log.d(TAG, "No events to flush");
                return false;
            }
            
            // Send each event individually as per API schema
            JSONArray requestBody = new JSONArray();
            for (Map<String, Object> event : events) {
                JSONObject payload = new JSONObject();
                payload.put("sessionId", event.get("session_id"));
                payload.put("userId", event.get("user_id"));
                payload.put("timestamp", event.get("timestamp"));
                payload.put("event_type", event.get("type"));
                payload.put("event_name", event.getOrDefault("event_name", "unknown"));
                payload.put("event_payload", new JSONObject((Map<String, Object>) event.getOrDefault("data", new HashMap<>())));
                payload.put("metadata", new JSONObject((Map<String, Object>) event.getOrDefault("metadata", new HashMap<>())));
                payload.put("os", getOSInfo());
                requestBody.put(payload);
            }
            boolean success = sendBatchToBackend(EVENTS_ENDPOINT, requestBody);

            if (success || !keepOnFailure) {
                // Delete only the specific events that were fetched
                storageManager.deleteEvents(events);
                
                if (config.enableStats && totalRecordsSent != null) {
                    totalRecordsSent.addAndGet(events.size());
                }
                
                Log.i(TAG, "Successfully flushed " + events.size() + " events");
            }
            
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error flushing events", e);
            return false;
        }
    }
    /**
     * Flush logs data to backend using new file-based mechanism
     * Uses the new directory-based approach: move files from storing to flushing, process entire files
     */
    private boolean flushLogs(ConfigManager.LogsConfig logsConfig, boolean keepOnFailure) {
        try {
            // Step 1: Move files from storing to flushing directory
            FileStorageProvider.FlushPrepResult prepResult = storageManager.prepareFilesForFlushing(logsConfig.whitelistedLogLevels);
            
            if (!prepResult.hasFiles()) {
                Log.d(TAG, "No log files to flush");
                return false;
            }
            
            int totalLogsSent = 0;
            int totalFilesProcessed = 0;
            boolean overallSuccess = true;
            
            // Step 2: Process each level's files
            for (String level : prepResult.getLevels()) {
                List<java.io.File> levelFiles = prepResult.getFilesForLevel(level);
                
                for (java.io.File logFile : levelFiles) {
                    try {
                        // Step 3: Read entire file as JSONArray
                        JSONArray fileLogs = storageManager.readFileAsJSONArray(logFile);
                        
                        if (fileLogs.length() > 0) {
                            // Step 4: Send entire file content as batch to backend
                            boolean fileSuccess = sendBatchToBackend(LOGS_ENDPOINT, fileLogs);
                            
                            if (fileSuccess || !keepOnFailure) {
                                // Step 5: Delete the entire file after successful transmission (or if not keeping on failure)
                                boolean deleted = storageManager.deleteProcessedFile(logFile);
                                if (deleted) {
                                    totalLogsSent += fileLogs.length();
                                    totalFilesProcessed++;
                                    Log.d(TAG, "Successfully processed file: " + logFile.getName() + 
                                              " (" + fileLogs.length() + " logs)");
                                } else {
                                    Log.w(TAG, "Failed to delete processed file: " + logFile.getName());
                                }
                            } else {
                                Log.w(TAG, "Backend transmission failed for file: " + logFile.getName() + 
                                           ", keeping file for retry");
                                overallSuccess = false;
                            }
                        } else {
                            // Empty file - just delete it
                            storageManager.deleteProcessedFile(logFile);
                            totalFilesProcessed++;
                            Log.d(TAG, "Deleted empty file: " + logFile.getName());
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing log file: " + logFile.getName(), e);
                        overallSuccess = false;
                        
                        // Decide whether to delete file even on error (to prevent infinite retry loops)
                        if (!keepOnFailure) {
                            storageManager.deleteProcessedFile(logFile);
                            Log.w(TAG, "Deleted problematic file: " + logFile.getName());
                        }
                    }
                }
            }
            
            // Step 6: Clean up non-whitelisted logs (files outside whitelisted levels)
            int deletedNonWhitelisted = storageManager.deleteNonWhitelistedLogs(logsConfig.whitelistedLogLevels);
            
            if (config.enableStats && totalRecordsSent != null) {
                totalRecordsSent.addAndGet(totalLogsSent);
            }
            
            Log.i(TAG, "File-based log flush completed - Processed " + totalFilesProcessed + " files, " + 
                       totalLogsSent + " logs sent" + 
                       (deletedNonWhitelisted > 0 ? ", cleaned up " + deletedNonWhitelisted + " non-whitelisted files" : ""));
            
            return overallSuccess;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in file-based log flushing", e);
            return false;
        }
    }
    
    /**
     * Send batch of data to backend endpoint
     */
    private boolean sendBatchToBackend(String endpoint, JSONArray batchPayload) {
        HttpURLConnection connection = null;
        
        try {
            URL url = new URL(baseApiUrl + endpoint);
            connection = (HttpURLConnection) url.openConnection();
            
            // Configure connection
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", CONTENT_TYPE);
            
            // Add authentication if API key is provided
            if (apiKey != null && !apiKey.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            
            // Send batch payload
            byte[] jsonBytes = batchPayload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(jsonBytes.length));
            
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(jsonBytes);
                outputStream.flush();
            }
            
            // Check response
            int responseCode = connection.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                Log.d(TAG, "Successfully sent batch of " + batchPayload.length() + " logs to " + endpoint + " (response: " + responseCode + ")");
                return true;
            } else {
                String errorResponse = readErrorResponse(connection);
                Log.w(TAG, "Failed to send batch to " + endpoint + " (response: " + responseCode + ", error: " + errorResponse + ")");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending batch to " + endpoint, e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Send data to backend endpoint
     */
    private boolean sendDataToBackend(String endpoint, JSONObject payload) {
        HttpURLConnection connection = null;
        
        try {
            URL url = new URL(baseApiUrl + endpoint);
            connection = (HttpURLConnection) url.openConnection();
            
            // Configure connection
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", CONTENT_TYPE);
            
            // Add authentication if API key is provided
            if (apiKey != null && !apiKey.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            
            // Send payload
            byte[] jsonBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(jsonBytes.length));
            
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(jsonBytes);
                outputStream.flush();
            }
            
            // Check response
            int responseCode = connection.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                Log.d(TAG, "Successfully sent data to " + endpoint + " (response: " + responseCode + ")");
                return true;
            } else {
                String errorResponse = readErrorResponse(connection);
                Log.w(TAG, "Failed to send data to " + endpoint + " (response: " + responseCode + ", error: " + errorResponse + ")");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending data to " + endpoint, e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Read error response from connection
     */
    private String readErrorResponse(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } catch (Exception e) {
            return "Unable to read error response";
        }
    }
    
    /**
     * Get processing statistics
     */
    public FlushingStats getStats() {
        if (!config.enableStats) {
            return new FlushingStats(0, 0, 0, 0, false);
        }
        
        return new FlushingStats(
            totalFlushAttempts != null ? totalFlushAttempts.get() : 0,
            totalFlushSuccesses != null ? totalFlushSuccesses.get() : 0,
            totalFlushFailures != null ? totalFlushFailures.get() : 0,
            totalRecordsSent != null ? totalRecordsSent.get() : 0,
            true
        );
    }
    
    /**
     * Check if flushing is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Check if currently flushing
     */
    public boolean isFlushing() {
        return isFlushing.get();
    }
    
    /**
     * Shutdown the DataFlusher
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down DataFlusher");
        
        stopFlushing();
        
        // Perform final flush
        if (config.flushOnShutdown) {
            flushNow();
        }
        
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        Log.i(TAG, "DataFlusher shutdown complete");
    }
    
    // Helper methods
    
    private String getOSInfo() {
        return "ANDROID";
    }
    
    // ConfigUpdateListener implementation
    
    @Override
    public void onConfigUpdated(JSONObject newConfig) {
        ConfigManager.DataFlusherConfig newFlusherConfig = configManager.getDataFlusherConfig();
        this.config = newFlusherConfig;
        updateStatsConfiguration();
        
        // Restart flushing with new interval if it's running
        if (isRunning.get()) {
            stopFlushing();
            startFlushing();
        }
        
        Log.i(TAG, "DataFlusher configuration updated - interval: " + config.flushIntervalMs + "ms");
    }
    
    @Override
    public void onConfigError(String error) {
        Log.e(TAG, "Configuration error: " + error);
        // Continue with current configuration
    }
    
    private void updateStatsConfiguration() {
        if (config.enableStats) {
            if (totalFlushAttempts == null) {
                totalFlushAttempts = new AtomicLong(0);
            }
            if (totalFlushSuccesses == null) {
                totalFlushSuccesses = new AtomicLong(0);
            }
            if (totalFlushFailures == null) {
                totalFlushFailures = new AtomicLong(0);
            }
            if (totalRecordsSent == null) {
                totalRecordsSent = new AtomicLong(0);
            }
            Log.i(TAG, "Statistics enabled for DataFlusher");
        } else {
            totalFlushAttempts = null;
            totalFlushSuccesses = null;
            totalFlushFailures = null;
            totalRecordsSent = null;
            Log.i(TAG, "Statistics disabled for DataFlusher");
        }
    }
    
    // Inner classes
    
    /**
     * Flushing statistics
     */
    public static class FlushingStats {
        public final long totalAttempts;
        public final long totalSuccesses;
        public final long totalFailures;
        public final long totalRecordsSent;
        public final boolean statsEnabled;
        
        public FlushingStats(long totalAttempts, long totalSuccesses, long totalFailures, 
                            long totalRecordsSent, boolean statsEnabled) {
            this.totalAttempts = totalAttempts;
            this.totalSuccesses = totalSuccesses;
            this.totalFailures = totalFailures;
            this.totalRecordsSent = totalRecordsSent;
            this.statsEnabled = statsEnabled;
        }
        
        public String getFormattedStats() {
            if (!statsEnabled) {
                return "Statistics are disabled for DataFlusher";
            }

            return "DataFlusher Stats:\n" +
                    "Flush Attempts: " + totalAttempts + "\n" +
                    "Flush Successes: " + totalSuccesses + "\n" +
                    "Flush Failures: " + totalFailures + "\n" +
                    "Total Records Sent: " + totalRecordsSent + "\n" +
                    "Success Rate: " + String.format(Locale.ENGLISH, "%.2f%%", getSuccessRate() * 100) + "\n";
        }
        
        public double getSuccessRate() {
            if (!statsEnabled || totalAttempts == 0) return 1.0;
            return (double) totalSuccesses / totalAttempts;
        }
    }
    
    // Public methods for startup data recovery
    
    /**
     * Flush specific metrics and API latencies during startup recovery
     * Used by AppMonitor.flushExistingDataOnStartup()
     */
    public boolean flushMetricsForRecovery(List<Map<String, Object>> metrics, List<Map<String, Object>> apiLatencies) {
        if (baseApiUrl == null) {
            Log.w(TAG, "Cannot flush for recovery - base API URL not configured");
            return false;
        }
        
        try {
            // Execute on background thread and wait for completion to avoid NetworkOnMainThreadException
            return executor.submit(() -> {
                return flushMetrics(metrics, apiLatencies, false); // keepOnFailure=false for recovery
            }).get(); // Wait for completion
        } catch (Exception e) {
            Log.e(TAG, "Error in metrics recovery flush", e);
            return false;
        }
    }
    
    /**
     * Flush specific events during startup recovery
     * Used by AppMonitor.flushExistingDataOnStartup()
     */
    public boolean flushEventsForRecovery(List<Map<String, Object>> events) {
        if (baseApiUrl == null) {
            Log.w(TAG, "Cannot flush for recovery - base API URL not configured");
            return false;
        }
        
        try {
            // Execute on background thread and wait for completion to avoid NetworkOnMainThreadException
            return executor.submit(() -> {
                return flushEvents(events, false); // keepOnFailure=false for recovery
            }).get(); // Wait for completion
        } catch (Exception e) {
            Log.e(TAG, "Error in events recovery flush", e);
            return false;
        }
    }
    
    /**
     * Flush logs during startup recovery using new file-based mechanism
     * Used by AppMonitor.flushExistingDataOnStartup()
     */
    public boolean flushLogsForRecovery(ConfigManager.LogsConfig logsConfig) {
        if (baseApiUrl == null) {
            Log.w(TAG, "Cannot flush for recovery - base API URL not configured");
            return false;
        }
        
        try {
            // Execute on background thread and wait for completion to avoid NetworkOnMainThreadException
            return executor.submit(() -> {
                // keepOnFailure=false for recovery

                // Then process any files in storing directory
                return flushLogs(logsConfig, false);
            }).get(); // Wait for completion
        } catch (Exception e) {
            Log.e(TAG, "Error in logs recovery flush", e);
            return false;
        }
    }
    
    /**
     * Process files that are already in flushing directory (for recovery)
     */
    private int processFilesForRecovery(FileStorageProvider.FlushPrepResult prepResult, ConfigManager.LogsConfig logsConfig) {
        int totalLogsSent = 0;
        int totalFilesProcessed = 0;
        
        for (String level : prepResult.getLevels()) {
            List<java.io.File> levelFiles = prepResult.getFilesForLevel(level);
            
            for (java.io.File logFile : levelFiles) {
                try {
                    JSONArray fileLogs = storageManager.readFileAsJSONArray(logFile);
                    
                    if (fileLogs.length() > 0) {
                        boolean fileSuccess = sendBatchToBackend(LOGS_ENDPOINT, fileLogs);
                        totalLogsSent += fileLogs.length();
                    }
                    
                    // Always delete file during recovery (even if backend fails) to prevent infinite retry loops
                    storageManager.deleteProcessedFile(logFile);
                    totalFilesProcessed++;
                    
                    Log.d(TAG, "Recovery processed file: " + logFile.getName() + " (" + fileLogs.length() + " logs)");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error processing recovery file: " + logFile.getName(), e);
                    // Still delete the file to prevent infinite retry loops
                    storageManager.deleteProcessedFile(logFile);
                    totalFilesProcessed++;
                }
            }
        }
        
        Log.i(TAG, "Recovery processed " + totalFilesProcessed + " existing files, " + totalLogsSent + " logs");
        return totalLogsSent; // Return log count for statistics
    }
    
    /**
     * Flush any existing data from database on startup
     * This handles the edge case where the app was killed and restarted with existing data
     * Processes ALL existing data in batches until database is empty
     */
    public void flushExistingDataOnStartup() {
        new Thread(() -> {
            try {
                // Check if this feature is enabled in configuration
                ConfigManager.DataFlusherConfig dataFlusherConfig = config;
                if (!dataFlusherConfig.flushExistingDataOnStartup) {
                    Log.d(TAG, "Flush existing data on startup is disabled in configuration");
                    return;
                }

                Log.i(TAG, "Checking for existing data from previous session...");

                // Get the batch size from configuration
                int batchSize = dataFlusherConfig.batchSize;

                // Capture maximum IDs at the start to avoid processing new data added during recovery
                long maxMetricsId = storageManager.getMaxMetricsId();
                long maxEventsId = storageManager.getMaxEventsId();
                long maxApiLatenciesId = storageManager.getMaxApiLatenciesId();

                // Log the boundaries we're working with
                Log.i(TAG, "Recovery boundaries - Metrics ID: " + maxMetricsId +
                        ", Events ID: " + maxEventsId +
                        ", API Latencies ID: " + maxApiLatenciesId);

                // If all max IDs are 0, there's no existing data
                if (maxMetricsId == 0 && maxEventsId == 0 && maxApiLatenciesId == 0) {
                    Log.i(TAG, "No existing data found from previous session");
                    return;
                }

                // Start flushing if not already running
                boolean wasRunning = isRunning.get();
                if (!wasRunning) {
                    startFlushing();
                }

                boolean hasDataToProcess = true;
                int totalMetricsProcessed = 0;
                int totalEventsProcessed = 0;
                int totalApiLatenciesProcessed = 0;
                boolean logsProcessed = false;

                while (hasDataToProcess) {
                    hasDataToProcess = false;

                    // Process metrics and API latencies together (they're sent in the same payload)
                    if (dataFlusherConfig.enableMetricsFlush && (maxMetricsId > 0 || maxApiLatenciesId > 0)) {
                        // Get the batch sizes before processing
                        List<Map<String, Object>> metricsBatch = storageManager.getMetricsUpToId(batchSize, maxMetricsId);
                        List<Map<String, Object>> apiLatenciesBatch = storageManager.getApiLatenciesUpToId(batchSize, maxApiLatenciesId);

                        if (!metricsBatch.isEmpty() || !apiLatenciesBatch.isEmpty()) {
                            hasDataToProcess = true;
                            totalMetricsProcessed += metricsBatch.size();
                            totalApiLatenciesProcessed += apiLatenciesBatch.size();

                            // Process the batch
                            flushMetricsForRecovery(metricsBatch, apiLatenciesBatch);

                            Log.d(TAG, "Processed batch: " + metricsBatch.size() + " metrics, " + apiLatenciesBatch.size() + " API latencies");
                        }
                    }

                    // Process events
                    if (dataFlusherConfig.enableEventsFlush && maxEventsId > 0) {
                        // Get the batch size before processing
                        List<Map<String, Object>> eventsBatch = storageManager.getEventsUpToId(batchSize, maxEventsId);

                        if (!eventsBatch.isEmpty()) {
                            hasDataToProcess = true;
                            totalEventsProcessed += eventsBatch.size();

                            // Process the batch
                            flushEventsForRecovery(eventsBatch);

                            Log.d(TAG, "Processed batch: " + eventsBatch.size() + " events");
                        }
                    }

                    // Process logs using new file-based mechanism
                    if (dataFlusherConfig.enableLogsFlush) {
                        ConfigManager.LogsConfig logsConfig = configManager.getLogsConfig();
                        logsProcessed = flushLogsForRecovery(logsConfig);
                    }
                }

                // Log summary of processed data
                if (totalMetricsProcessed > 0 || totalEventsProcessed > 0 ||
                        totalApiLatenciesProcessed > 0 || logsProcessed) {

                    Log.i(TAG, "Existing data recovery completed - Processed: " +
                            totalMetricsProcessed + " metrics, " +
                            totalEventsProcessed + " events, " +
                            totalApiLatenciesProcessed + " API latencies, " +
                            " logs processed " + logsProcessed);
                } else {
                    Log.i(TAG, "No existing data found from previous session");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error during existing data flush on startup", e);
                // Don't let this fail the startup process
            }
        }).start();
    }
} 