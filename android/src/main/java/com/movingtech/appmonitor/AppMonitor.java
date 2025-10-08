package com.movingtech.appmonitor;

import android.content.Context;
import android.util.Log;

import com.movingtech.appmonitor.apihelper.ApiClient;
import com.movingtech.appmonitor.apihelper.ApiResponseCallback;
import com.movingtech.appmonitor.storage.StorageManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AppMonitor provides a unified interface for all monitoring functionality
 * Manages configuration through ConfigManager and coordinates all aggregators
 * This is a singleton class that manages the entire monitoring system
 */
public class AppMonitor implements ConfigManager.ConfigUpdateListener {
    
    private static final String TAG = "AppMonitor";
    
    // Singleton instance
    private static volatile AppMonitor instance;
    // Core components
    private final Context context;
    private final ConfigManager configManager;
    private final StorageManager storageManager;
    
    // Aggregators
    private final MetricsAggregator metricsAggregator;
    private final EventsAggregator eventsAggregator;
    private final LogsAggregator logsAggregator;
    private final ApiLatenciesAggregator apiLatenciesAggregator;
    
    // Data flusher
    private final DataFlusher dataFlusher;
    
    // Metrics fetcher
    private final MetricsFetcher metricsFetcher;
    
    // State
    private static boolean isInitialized = false;
    private String userId = "unknown";
    private String appMonitorUrl;
    private String sessionId;
    private final String replaceUserEp = "/ingest/replace";
    
    private AppMonitor(Context context) {
        this.context = context.getApplicationContext();
        
        // Initialize ConfigManager
        this.configManager = ConfigManager.getInstance(context);
        
        // Generate session ID for this monitoring session
        this.sessionId = generateSessionId();
        
        // Initialize StorageManager with configuration
        ConfigManager.StorageConfig storageConfig = configManager.getStorageConfig();
        this.storageManager = new StorageManager(context, storageConfig.databaseName, storageConfig.databaseVersion);
        
        // Initialize aggregators - they will get their config from ConfigManager
        this.metricsAggregator = new MetricsAggregator(context, storageManager);
        this.eventsAggregator = new EventsAggregator(context, storageManager);
        this.logsAggregator = new LogsAggregator(context);
        this.apiLatenciesAggregator = new ApiLatenciesAggregator(context, storageManager);
        
        // Connect LogsAggregator to the Sessionizer from StorageManager
        this.logsAggregator.setSessionizer(storageManager.getSessionizer());
        
        // Initialize DataFlusher
        this.dataFlusher = new DataFlusher(context, storageManager);
        
        // Initialize MetricsFetcher
        this.metricsFetcher = new MetricsFetcher(context);
        
        // Set session context for all components
        setSessionContextForAllComponents();
        
        // Set up MetricsFetcher with MetricsAggregator
        this.metricsFetcher.setMetricsAggregator(metricsAggregator);
        
        // Register for config updates
        configManager.setConfigUpdateListener(this);
        
        Log.i(TAG, "AppMonitor initialized with session ID: " + sessionId);
    }
    
    /**
     * Get singleton instance
     */
    public static AppMonitor getInstance(Context context) {
        if (instance == null) {
            synchronized (AppMonitor.class) {
                if (instance == null) {
                    instance = new AppMonitor(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize with API configuration
     */
    public void initialize(String configApiUrl, String apiKey, String userId) {
        configManager.initialize(configApiUrl, apiKey, userId);
        
        // Initialize DataFlusher with API configuration
        dataFlusher.initialize(configApiUrl, apiKey);
        
        // Set user context from config
        ConfigManager.AppConfig appConfig = configManager.getAppConfig();
        setUserId(userId);

        isInitialized = true;
        this.appMonitorUrl = configApiUrl;

        Log.i(TAG, "AppMonitor initialized with API config");
        // Start all aggregators
        startMonitoring();
        Log.i(TAG, "Started Monitoring");

    }
    
    /**
     * Initialize with API configuration and custom refresh interval
     */
    public void initialize(String configApiUrl, String apiKey, long refreshIntervalMs, String userId) {
        configManager.initialize(configApiUrl, apiKey, refreshIntervalMs, userId);
        
        // Initialize DataFlusher with API configuration
        dataFlusher.initialize(configApiUrl, apiKey);
        
        // Set user context from config
        ConfigManager.AppConfig appConfig = configManager.getAppConfig();
        setUserId(userId);
        
        // Start all aggregators
        startMonitoring();
        
        isInitialized = true;
        Log.i(TAG, "AppMonitor initialized with API config and custom refresh interval");
    }
    
    /**
     * Initialize with default configuration (no API)
     */
    public void initializeWithDefaults() {
        // Start all aggregators with default config
        startMonitoring();
        
        isInitialized = true;
        Log.i(TAG, "AppMonitor initialized with default configuration");
    }
    
    /**
     * Set user ID for all aggregators
     */
    public void setUserId(String userId) {
        this.userId = userId;
        metricsAggregator.setUserContext(userId);
        eventsAggregator.setUserContext(userId);
        logsAggregator.setUserContext(userId);
        apiLatenciesAggregator.setUserContext(userId);
        storageManager.getSessionizer().setUserContext(userId);
        dataFlusher.setUserContext(userId);
        Log.i(TAG, "User ID updated: " + userId);
    }

    public CompletableFuture<Boolean> replaceUserId(String userId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        JSONObject jsonBody = new JSONObject();
        if(this.userId.equals(userId)) {
            future.complete(true);
            return future;
        }
        try {
            jsonBody.put("oldUserId", this.userId);
            jsonBody.put("newUserId", userId);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            future.complete(false);
        }
        ApiClient.request(appMonitorUrl+replaceUserEp, "POST", jsonBody.toString(), new HashMap<>(), new ApiResponseCallback() {
            @Override
            public void onSuccess(String responseBody) {
                setUserId(userId);
                future.complete(true);
            }

            @Override
            public void onError(String errorMessage, int code) {
                future.complete(false);
            }
        });
        return future;
    }
    
    /**
     * Set session ID for all components
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
        setSessionContextForAllComponents();
        Log.i(TAG, "Session ID updated: " + sessionId);
    }
    
    /**
     * Get current session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Generate a new session ID and update all components
     */
    public void generateNewSession() {
        this.sessionId = generateSessionId();
        setSessionContextForAllComponents();
        Log.i(TAG, "New session generated: " + sessionId);
    }
    
    /**
     * Set session context for all components
     */
    private void setSessionContextForAllComponents() {
        metricsAggregator.setSessionContext(sessionId);
        eventsAggregator.setSessionContext(sessionId);
        logsAggregator.setSessionContext(sessionId);
        apiLatenciesAggregator.setSessionContext(sessionId);
        storageManager.getSessionizer().setSessionContext(sessionId);
        dataFlusher.setSessionContext(sessionId);
    }
    
    /**
     * Start all monitoring processes
     */
    public void startMonitoring() {
        if (!isInitialized) {
            Log.w(TAG, "Cannot start monitoring - not initialized");
            return;
        }
        
        // First, flush any existing data from previous sessions (edge case: app was killed and restarted)
        flushExistingDataOnStartup();
        
        metricsAggregator.startProcessing();
        eventsAggregator.startProcessing();
        logsAggregator.startProcessing();
        apiLatenciesAggregator.startProcessing();
        dataFlusher.startFlushing();
        
        // Start metrics fetcher
        metricsFetcher.startMetricsFetcher();
        // Start API intercepting
        startApiIntercepting();
        
        Log.i(TAG, "All monitoring processes started");
    }

    /**
     * Flush any existing data from database on startup
     * This handles the edge case where the app was killed and restarted with existing data
     * Processes ALL existing data in batches until database is empty
     */
    private void flushExistingDataOnStartup() {
        // Delegate to DataFlusher for existing data recovery
        dataFlusher.flushExistingDataOnStartup();
    }

    private void startApiIntercepting() {
        ApiClient.init(context);
    }

    /**
     * Stop all monitoring processes
     */
    public void stopMonitoring() {
        metricsAggregator.stopProcessing();
        eventsAggregator.stopProcessing();
        logsAggregator.stopProcessing();
        apiLatenciesAggregator.stopProcessing();
        dataFlusher.stopFlushing();
        
        // Stop metrics fetcher
        metricsFetcher.stopMetricsFetcher();
        
        Log.i(TAG, "All monitoring processes stopped");
    }
    
    /**
     * Flush all aggregators
     */
    public void flush() {
        metricsAggregator.flush();
        eventsAggregator.flush();
        logsAggregator.flush();
        apiLatenciesAggregator.flush();
        dataFlusher.flushNow();
        
        Log.i(TAG, "All aggregators flushed");
    }
    
    /**
     * Refresh configuration from API
     */
    public void refreshConfiguration() {
        configManager.refreshConfig();
    }
    
    // Metrics methods
    
    /**
     * Add a metric
     */
    public boolean addMetric(String metricName, Number value) {
        return metricsAggregator.addMetric(metricName, value);
    }
    
    /**
     * Add a metric with metadata
     */
    public boolean addMetric(String metricName, Number value, Map<String, Object> metadata) {
        return metricsAggregator.addMetric(metricName, value, metadata);
    }
    
    /**
     * Add multiple metrics
     */
    public int addMetrics(List<MetricsAggregator.MetricEntry> metrics) {
        return metricsAggregator.addMetrics(metrics);
    }
    
    // Events methods
    
    /**
     * Add an event
     */
    public boolean addEvent(String eventType, String eventName, Map<String, Object> eventPayload) {
        return eventsAggregator.addEvent(eventType, eventName, eventPayload);
    }
    
    /**
     * Add an event with metadata
     */
    public boolean addEvent(String eventType, String eventName, Map<String, Object> eventPayload, 
                           Map<String, Object> metadata) {
        return eventsAggregator.addEvent(eventType, eventName, eventPayload, metadata);
    }
    
    /**
     * Add multiple events
     */
    public int addEvents(List<EventsAggregator.Event> events) {
        if (!isInitialized) {
            Log.w(TAG, "AppMonitor not initialized, cannot add events");
            return 0;
        }
        return eventsAggregator.addEvents(events);
    }
    
    // Logs methods
    
    /**
     * Add a log
     */
    public boolean addLog(String level, String message) {
        if (!isInitialized) {
            Log.w(TAG, "AppMonitor not initialized, cannot add log");
            return false;
        }
        return logsAggregator.addLog(level, message);
    }
    
    /**
     * Add a log with tag
     */
    public boolean addLog(String level, String message, String tag) {
        return logsAggregator.addLog(level, message, tag);
    }
    
    /**
     * Add a log with tag and labels
     */
    public boolean addLog(String level, String message, String tag, Map<String, String> labels) {
        return logsAggregator.addLog(level, message, tag, labels);
    }
    
    /**
     * Add multiple logs
     */
    public int addLogs(List<LogsAggregator.LogEntry> logs) {
        return logsAggregator.addLogs(logs);
    }
    
    // Statistics methods
    
    /**
     * Get metrics statistics
     */
    public MetricsAggregator.ProcessingStats getMetricsStats() {
        return metricsAggregator.getStats();
    }
    
    /**
     * Get events statistics
     */
    public EventsAggregator.ProcessingStats getEventsStats() {
        return eventsAggregator.getStats();
    }
    
    /**
     * Get logs statistics
     */
    public LogsAggregator.ProcessingStats getLogsStats() {
        return logsAggregator.getStats();
    }
    
    /**
     * Get API latencies statistics
     */
    public ApiLatenciesAggregator.ProcessingStats getApiLatenciesStats() {
        return apiLatenciesAggregator.getStats();
    }
    
    /**
     * Get DataFlusher instance for advanced operations
     */
    public DataFlusher getDataFlusher() {
        return dataFlusher;
    }
    
    /**
     * Get MetricsFetcher instance for advanced operations
     */
    public MetricsFetcher getMetricsFetcher() {
        return metricsFetcher;
    }
    
    /**
     * Get ApiLatenciesAggregator instance for advanced configuration or interceptor setup
     */
    public ApiLatenciesAggregator getApiLatenciesAggregator() {
        return apiLatenciesAggregator;
    }
    
    /**
     * Get formatted monitoring status
     */
    public String getMonitoringStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== App Monitor Status ===\n");
        status.append("Initialized: ").append(isInitialized).append("\n");
        status.append("User ID: ").append(userId).append("\n");
        status.append("Session ID: ").append(sessionId).append("\n");
        status.append("Config Available: ").append(configManager.isConfigAvailable()).append("\n");
        status.append("Last Config Update: ").append(new java.util.Date(configManager.getLastUpdateTime())).append("\n\n");
        
        // Current configuration
        ConfigManager.MetricsConfig metricsConfig = configManager.getMetricsConfig();
        ConfigManager.EventsConfig eventsConfig = configManager.getEventsConfig();
        ConfigManager.LogsConfig logsConfig = configManager.getLogsConfig();
        
        status.append("=== Current Configuration ===\n");
        status.append("Metrics - Batch: ").append(metricsConfig.batchSize)
              .append(", Interval: ").append(metricsConfig.processIntervalMs)
              .append("ms, Queue: ").append(metricsConfig.maxQueueSize)
              .append(", Stats: ").append(metricsConfig.enableStats)
              .append(", Aggregation: ").append(metricsConfig.enableAggregation).append("\n");
        
        status.append("Events - Batch: ").append(eventsConfig.batchSize)
              .append(", Interval: ").append(eventsConfig.processIntervalMs)
              .append("ms, Queue: ").append(eventsConfig.maxQueueSize)
              .append(", Stats: ").append(eventsConfig.enableStats).append("\n");
        
        status.append("Logs - Batch: ").append(logsConfig.batchSize)
              .append(", Interval: ").append(logsConfig.processIntervalMs)
              .append("ms, Queue: ").append(logsConfig.maxQueueSize)
              .append(", MaxPerFile: ").append(logsConfig.maxLogsPerFile)
              .append(", Stats: ").append(logsConfig.enableStats).append("\n\n");
        
        // Statistics
        if (metricsConfig.enableStats) {
            status.append("=== Metrics Statistics ===\n");
            status.append(getMetricsStats().getFormattedStats()).append("\n");
        }
        
        if (eventsConfig.enableStats) {
            status.append("=== Events Statistics ===\n");
            status.append(getEventsStats().getFormattedStats()).append("\n");
        }
        
        if (logsConfig.enableStats) {
            status.append("=== Logs Statistics ===\n");
            status.append(getLogsStats().getFormattedStats()).append("\n");
        }
        
        // MetricsFetcher status
        status.append("=== MetricsFetcher Status ===\n");
        status.append("Running: ").append(metricsFetcher.isRunning()).append("\n");
        status.append(metricsFetcher.getStats().getFormattedStats()).append("\n");
        
        return status.toString();
    }
    
    /**
     * Generate a unique session ID
     */
    private String generateSessionId() {
        return  UUID.randomUUID().toString(); // sessionId
    }
    
    /**
     * Get current configuration
     */
    public JSONObject getCurrentConfiguration() {
        return configManager.getCurrentConfig();
    }
    
    /**
     * Check if monitoring is enabled
     */
    public boolean isMonitoringEnabled() {
        return isInitialized;
    }
    
    /**
     * Shutdown all components
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down AppMonitor");
        
        stopMonitoring();
        flush();
        
        metricsAggregator.shutdown();
        eventsAggregator.shutdown();
        logsAggregator.shutdown();
        apiLatenciesAggregator.shutdown();
        dataFlusher.shutdown();
        
        // Shutdown metrics fetcher
        metricsFetcher.shutdown();
        
        storageManager.cleanup();
        configManager.shutdown();
        
        isInitialized = false;
        Log.i(TAG, "AppMonitor shutdown complete");
    }
    
    // ConfigUpdateListener implementation
    
    @Override
    public void onConfigUpdated(JSONObject newConfig) {
        Log.i(TAG, "Configuration updated in AppMonitor");
        
        // Update user context if changed
        ConfigManager.AppConfig appConfig = configManager.getAppConfig();
        
        // Aggregators will handle their own config updates
    }
    
    @Override
    public void onConfigError(String error) {
        Log.e(TAG, "Configuration error in AppMonitor: " + error);
        // Continue with current configuration
    }
    
    // API Latency methods
    
    /**
     * Add API latency data
     */
    public boolean addApiLatency(String endpoint, String method, long latency, int statusCode) {
        if (!isInitialized) {
            Log.w(TAG, "AppMonitor not initialized, cannot add API latency");
            return false;
        }
        return apiLatenciesAggregator.addApiLatency(endpoint, method, latency, statusCode);
    }
    
    /**
     * Add API latency data with custom timestamp
     */
    public boolean addApiLatency(String endpoint, String method, long latency, int statusCode, long timestamp) {
        if (!isInitialized) {
            Log.w(TAG, "AppMonitor not initialized, cannot add API latency");
            return false;
        }
        return apiLatenciesAggregator.addApiLatency(endpoint, method, latency, statusCode, timestamp);
    }
    
    /**
     * Add multiple API latencies at once
     */
    public int addApiLatencies(List<ApiLatenciesAggregator.ApiLatency> apiLatencies) {
        if (!isInitialized) {
            Log.w(TAG, "AppMonitor not initialized, cannot add API latencies");
            return 0;
        }
        return apiLatenciesAggregator.addApiLatencies(apiLatencies);
    }



    public static boolean isInitialized() {
        return isInitialized;
    }
}