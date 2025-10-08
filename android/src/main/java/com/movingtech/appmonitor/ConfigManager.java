package com.movingtech.appmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashSet;
import java.util.Set;

import static com.movingtech.appmonitor.ConfigDefaults.*;

/**
 * ConfigManager handles fetching, storing, and providing configuration for all aggregators
 * Supports API-based configuration with local caching and fallback defaults
 */
public class ConfigManager {
    
    private static final String TAG = "ConfigManager";
    private static final String PREFS_NAME = "app_monitor_config";
    private static final String KEY_CONFIG_JSON = "config_json";
    private static final String KEY_LAST_UPDATED = "last_updated";
    private static final String KEY_CONFIG_VERSION = "config_version";
    
    // Default configuration refresh interval (30 minutes)
    private static final long DEFAULT_REFRESH_INTERVAL_MS = 30 * 60 * 1000;
    
    // Singleton instance
    private static volatile ConfigManager instance;
    
    // Context and storage
    private final Context context;
    private final SharedPreferences prefs;
    
    // Configuration state
    private volatile JSONObject currentConfig;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isFetching = new AtomicBoolean(false);
    private final AtomicLong lastUpdateTime = new AtomicLong(0);
    
    // API configuration
    private String configApiUrl;
    private String apiKey;
    private String userId;
    private long refreshIntervalMs = DEFAULT_REFRESH_INTERVAL_MS;
    
    // Background refresh
    private final ScheduledExecutorService refreshExecutor;
    private ScheduledFuture<?> refreshTask;
    
    // Configuration listeners
    public interface ConfigUpdateListener {
        void onConfigUpdated(JSONObject newConfig);
        void onConfigError(String error);
    }
    
    private ConfigUpdateListener updateListener;
    
    private ConfigManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.refreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ConfigManager-Refresh"));
        
        // Load cached config on initialization
        loadCachedConfig();
        
        Log.i(TAG, "ConfigManager initialized");
    }
    
    /**
     * Get singleton instance
     */
    public static ConfigManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize with API configuration
     */
    public void initialize(String apiUrl, String apiKey, String userId) {
        initialize(apiUrl, apiKey, DEFAULT_REFRESH_INTERVAL_MS, userId);
    }
    
    /**
     * Initialize with API configuration and custom refresh interval
     */
    public void initialize(String apiUrl, String apiKey, long refreshIntervalMs, String userId) {
        this.configApiUrl = apiUrl;
        this.apiKey = apiKey;
        this.refreshIntervalMs = refreshIntervalMs;
        this.userId = userId;
        
        Log.i(TAG, "Initialized with API URL: " + apiUrl + ", refresh interval: " + refreshIntervalMs + "ms");
        
        // Fetch initial config if we don't have cached config or it's old
        if (currentConfig == null || isConfigStale()) {
            fetchConfigFromApi();
        }
        
        // Start periodic refresh
        startPeriodicRefresh();
        
        isInitialized.set(true);
    }
    
    /**
     * Set configuration update listener
     */
    public void setConfigUpdateListener(ConfigUpdateListener listener) {
        this.updateListener = listener;
    }
    
    /**
     * Get current configuration
     */
    public JSONObject getCurrentConfig() {
        if (currentConfig != null) {
            return currentConfig;
        }
        
        // Return default config if no config available
        return getDefaultConfig();
    }
    
    /**
     * Force refresh configuration from API
     */
    public void refreshConfig() {
        Log.i(TAG, "Manual config refresh requested");
        fetchConfigFromApi();
    }
    
    /**
     * Get metrics aggregator configuration
     */
    public MetricsConfig getMetricsConfig() {
        JSONObject config = getCurrentConfig();
        JSONObject metricsJson = config.optJSONObject("metrics");
        if (metricsJson == null) {
            return new MetricsConfig(); // Return defaults
        }
        
        return new MetricsConfig(
            metricsJson.optInt("batchSize", DEFAULT_BATCH_SIZE),
            metricsJson.optLong("processIntervalMs", DEFAULT_PROCESS_INTERVAL_MS),
            metricsJson.optInt("maxQueueSize", DEFAULT_MAX_QUEUE_SIZE),
            metricsJson.optBoolean("enableStats", DEFAULT_ENABLE_STATS),
            metricsJson.optBoolean("enableAggregation", DEFAULT_ENABLE_AGGREGATION)
        );
    }
    
    /**
     * Get events aggregator configuration
     */
    public EventsConfig getEventsConfig() {
        JSONObject config = getCurrentConfig();
        JSONObject eventsJson = config.optJSONObject("events");
        if (eventsJson == null) {
            return new EventsConfig(); // Return defaults
        }
        
        return new EventsConfig(
            eventsJson.optInt("batchSize", DEFAULT_BATCH_SIZE),
            eventsJson.optLong("processIntervalMs", DEFAULT_PROCESS_INTERVAL_MS),
            eventsJson.optInt("maxQueueSize", DEFAULT_MAX_QUEUE_SIZE),
            eventsJson.optBoolean("enableStats", DEFAULT_ENABLE_STATS)
        );
    }
    
    /**
     * Get API latencies aggregator configuration
     */
    public ApiLatenciesConfig getApiLatenciesConfig() {
        JSONObject config = getCurrentConfig();
        JSONObject apiLatenciesJson = config.optJSONObject("apiLatencies");
        if (apiLatenciesJson == null) {
            return new ApiLatenciesConfig(); // Return defaults
        }
        
        return new ApiLatenciesConfig(
            apiLatenciesJson.optInt("batchSize", DEFAULT_BATCH_SIZE),
            apiLatenciesJson.optLong("processIntervalMs", DEFAULT_PROCESS_INTERVAL_MS),
            apiLatenciesJson.optInt("maxQueueSize", DEFAULT_MAX_QUEUE_SIZE),
            apiLatenciesJson.optBoolean("enableStats", DEFAULT_ENABLE_STATS)
        );
    }
    
    /**
     * Get logs aggregator configuration
     */
    public LogsConfig getLogsConfig() {
        JSONObject config = getCurrentConfig();
        JSONObject logsJson = config.optJSONObject("logs");
        if (logsJson == null) {
            return new LogsConfig(); // Return defaults
        }
        
        // Parse whitelisted log levels
        Set<String> whitelistedLevels = new HashSet<>();
        org.json.JSONArray levelsArray = logsJson.optJSONArray("levels");
        if (levelsArray != null) {
            for (int i = 0; i < levelsArray.length(); i++) {
                whitelistedLevels.add(levelsArray.optString(i));
            }
        }
        
        return new LogsConfig(
            logsJson.optInt("batchSize", DEFAULT_BATCH_SIZE),
            logsJson.optLong("processIntervalMs", DEFAULT_PROCESS_INTERVAL_MS),
            logsJson.optInt("maxQueueSize", DEFAULT_MAX_QUEUE_SIZE),
            logsJson.optInt("maxLogsPerFile", DEFAULT_MAX_LOGS_PER_FILE),
            logsJson.optBoolean("enableStats", DEFAULT_ENABLE_STATS),
            whitelistedLevels
        );
    }
    
    /**
     * Get storage configuration
     */
    public StorageConfig getStorageConfig() {
        JSONObject config = getCurrentConfig();
        JSONObject storageJson = config.optJSONObject("storage");
        if (storageJson == null) {
            return new StorageConfig(); // Return defaults
        }
        
        return new StorageConfig(
            storageJson.optString("databaseName", DEFAULT_DATABASE_NAME),
            storageJson.optInt("databaseVersion", DEFAULT_DATABASE_VERSION),
            storageJson.optLong("retentionDays", DEFAULT_RETENTION_DAYS),
            storageJson.optBoolean("enableSQLite", DEFAULT_ENABLE_SQLITE),
            storageJson.optBoolean("enableFileStorage", DEFAULT_ENABLE_FILE_STORAGE)
        );
    }
    
    /**
     * Get data flusher configuration
     */
    public DataFlusherConfig getDataFlusherConfig() {
        JSONObject config = getCurrentConfig();
        JSONObject dataFlusherJson = config.optJSONObject("dataFlusher");
        if (dataFlusherJson == null) {
            return new DataFlusherConfig(); // Return defaults
        }
        
        return new DataFlusherConfig(
            dataFlusherJson.optLong("flushIntervalMs", DEFAULT_FLUSH_INTERVAL_MS),
            dataFlusherJson.optLong("initialDelayMs", DEFAULT_FLUSH_INITIAL_DELAY_MS),
            dataFlusherJson.optInt("batchSize", DEFAULT_BATCH_SIZE),
            dataFlusherJson.optBoolean("enableStats", DEFAULT_ENABLE_STATS),
            dataFlusherJson.optBoolean("enableMetricsFlush", DEFAULT_ENABLE_METRICS_FLUSH),
            dataFlusherJson.optBoolean("enableEventsFlush", DEFAULT_ENABLE_EVENTS_FLUSH),
            dataFlusherJson.optBoolean("enableLogsFlush", DEFAULT_ENABLE_LOGS_FLUSH),
            dataFlusherJson.optBoolean("flushOnShutdown", DEFAULT_FLUSH_ON_SHUTDOWN),
            dataFlusherJson.optBoolean("flushExistingDataOnStartup", DEFAULT_FLUSH_EXISTING_DATA_ON_STARTUP)
        );
    }
    
    /**
     * Get metrics fetcher configuration
     */
    public MetricsFetcherConfig getMetricsFetcherConfig() {
        JSONObject config = getCurrentConfig();
        JSONObject fetcherJson = config.optJSONObject("metricsFetcher");
        if (fetcherJson == null) {
            return new MetricsFetcherConfig(); // Return defaults
        }
        
        return new MetricsFetcherConfig(
            fetcherJson.optLong("collectionIntervalMs", DEFAULT_COLLECTION_INTERVAL_MS),
            fetcherJson.optLong("initialDelayMs", DEFAULT_FETCHER_INITIAL_DELAY_MS),
            fetcherJson.optBoolean("enableMemoryMetrics", DEFAULT_ENABLE_MEMORY_METRICS),
            fetcherJson.optBoolean("enableBatteryMetrics", DEFAULT_ENABLE_BATTERY_METRICS),
            fetcherJson.optBoolean("enableTemperatureMetrics", DEFAULT_ENABLE_TEMPERATURE_METRICS),
            fetcherJson.optBoolean("enableStats", DEFAULT_ENABLE_STATS)
        );
    }
    
    /**
     * Get general app configuration
     */
    public AppConfig getAppConfig() {
        JSONObject config = getCurrentConfig();
        JSONObject appJson = config.optJSONObject("app");
        if (appJson == null) {
            return new AppConfig(); // Return defaults
        }
        
        return new AppConfig(
            appJson.optBoolean("enableDebugLogging", DEFAULT_ENABLE_DEBUG_LOGGING),
            appJson.optLong("sessionTimeoutMs", DEFAULT_SESSION_TIMEOUT_MS),
            appJson.optString("environment", DEFAULT_ENVIRONMENT)
        );
    }
    
    /**
     * Check if configuration is available
     */
    public boolean isConfigAvailable() {
        return currentConfig != null || hasValidCachedConfig();
    }
    
    /**
     * Get last configuration update time
     */
    public long getLastUpdateTime() {
        return lastUpdateTime.get();
    }
    
    /**
     * Check if config is stale (needs refresh)
     */
    private boolean isConfigStale() {
        long lastUpdate = prefs.getLong(KEY_LAST_UPDATED, 0);
        return (System.currentTimeMillis() - lastUpdate) > refreshIntervalMs;
    }
    
    /**
     * Load cached configuration from SharedPreferences
     */
    private void loadCachedConfig() {
        try {
            String cachedJson = prefs.getString(KEY_CONFIG_JSON, null);
            if (cachedJson != null) {
                currentConfig = new JSONObject(cachedJson);
                lastUpdateTime.set(prefs.getLong(KEY_LAST_UPDATED, 0));
                Log.i(TAG, "Loaded cached configuration");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading cached config", e);
            currentConfig = null;
        }
    }
    
    /**
     * Check if we have valid cached configuration
     */
    private boolean hasValidCachedConfig() {
        return prefs.contains(KEY_CONFIG_JSON) && !isConfigStale();
    }
    
    /**
     * Fetch configuration from API asynchronously
     */
    private void fetchConfigFromApi() {
        if (configApiUrl == null || isFetching.get()) {
            return;
        }
        
        refreshExecutor.execute(() -> {
            if (!isFetching.compareAndSet(false, true)) {
                return; // Already fetching
            }
            
            try {
                Log.i(TAG, "Fetching configuration from API: " + configApiUrl);

                HttpURLConnection connection = getConfig();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String jsonResponse = readResponse(connection.getInputStream());
                    JSONObject newConfig = new JSONObject(jsonResponse);
                    
                    // Update configuration
                    if(newConfig.has("data")){
                        JSONObject configData = newConfig.optJSONObject("data");
                        updateConfiguration(configData);
                    }
                    Log.i(TAG, "Successfully fetched configuration");
                } else {
                    String error = "API request failed with response code: " + responseCode;
                    Log.e(TAG, error);
                    if (updateListener != null) {
                        updateListener.onConfigError(error);
                    }
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching config from API", e);
                if (updateListener != null) {
                    updateListener.onConfigError("Failed to fetch config: " + e.getMessage());
                }
            } finally {
                isFetching.set(false);
            }
        });
    }

    @NonNull
    private HttpURLConnection getConfig() throws IOException {
        String configAPIUrl = configApiUrl + "/config?user_id="+ userId;
        URL url = new URL(configAPIUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Set request properties
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000); // 10 seconds
        connection.setReadTimeout(15000); // 15 seconds
        connection.setRequestProperty("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        return connection;
    }

    /**
     * Read HTTP response as string
     */
    private String readResponse(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String line;
        
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        
        reader.close();
        return response.toString();
    }
    
    /**
     * Update configuration and save to cache
     */
    private void updateConfiguration(JSONObject newConfig) {
        try {
            // Validate configuration
            if (isValidConfig(newConfig)) {
                currentConfig = newConfig;
                lastUpdateTime.set(System.currentTimeMillis());
                
                // Save to cache
                prefs.edit()
                    .putString(KEY_CONFIG_JSON, newConfig.toString())
                    .putLong(KEY_LAST_UPDATED, lastUpdateTime.get())
                    .putInt(KEY_CONFIG_VERSION, newConfig.optInt("version", 1))
                    .apply();
                
                // Notify listener
                if (updateListener != null) {
                    updateListener.onConfigUpdated(newConfig);
                }
                
                Log.i(TAG, "Configuration updated successfully");
            } else {
                Log.w(TAG, "Received invalid configuration, ignoring update");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating configuration", e);
        }
    }
    
    /**
     * Validate configuration structure
     */
    private boolean isValidConfig(JSONObject config) {
        // Basic validation - config should have at least one of the expected sections
        return config.has("metrics") || config.has("events") || config.has("logs") || 
               config.has("storage") || config.has("app");
    }
    
    /**
     * Get default configuration
     */
    private JSONObject getDefaultConfig() {
        try {
            JSONObject defaultConfig = new JSONObject();
            
            // Use config classes with their toJSON methods for consistency
            defaultConfig.put("metrics", new MetricsConfig().toJSON());
            defaultConfig.put("events", new EventsConfig().toJSON());
            defaultConfig.put("apiLatencies", new ApiLatenciesConfig().toJSON());
            defaultConfig.put("logs", new LogsConfig().toJSON());
            defaultConfig.put("storage", new StorageConfig().toJSON());
            defaultConfig.put("app", new AppConfig().toJSON());
            defaultConfig.put("dataFlusher", new DataFlusherConfig().toJSON());
            defaultConfig.put("metricsFetcher", new MetricsFetcherConfig().toJSON());
            
            defaultConfig.put("version", 1);
            
            return defaultConfig;
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating default config", e);
            return new JSONObject();
        }
    }

    /**
     * Start periodic configuration refresh
     */
    private void startPeriodicRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        
        refreshTask = refreshExecutor.scheduleWithFixedDelay(
            this::fetchConfigFromApi,
            0,
            refreshIntervalMs,
            TimeUnit.MILLISECONDS
        );
        
        Log.i(TAG, "Started periodic config refresh with interval: " + refreshIntervalMs + "ms");
    }
    
    /**
     * Stop periodic refresh and cleanup
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down ConfigManager");
        
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        
        refreshExecutor.shutdown();
        
        try {
            if (!refreshExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                refreshExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            refreshExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        Log.i(TAG, "ConfigManager shutdown complete");
    }
    
    // Configuration classes
    
    public static class MetricsConfig {
        public final int batchSize;
        public final long processIntervalMs;
        public final int maxQueueSize;
        public final boolean enableStats;
        public final boolean enableAggregation;
        
        public MetricsConfig() {
            this(DEFAULT_BATCH_SIZE, DEFAULT_PROCESS_INTERVAL_MS, DEFAULT_MAX_QUEUE_SIZE, DEFAULT_ENABLE_STATS, DEFAULT_ENABLE_AGGREGATION);
        }
        
        public MetricsConfig(int batchSize, long processIntervalMs, int maxQueueSize, 
                           boolean enableStats, boolean enableAggregation) {
            this.batchSize = batchSize;
            this.processIntervalMs = processIntervalMs;
            this.maxQueueSize = maxQueueSize;
            this.enableStats = enableStats;
            this.enableAggregation = enableAggregation;
        }
        
        /**
         * Convert to JSON object
         */
        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("batchSize", batchSize);
            json.put("processIntervalMs", processIntervalMs);
            json.put("maxQueueSize", maxQueueSize);
            json.put("enableStats", enableStats);
            json.put("enableAggregation", enableAggregation);
            return json;
        }
    }
    
    public static class EventsConfig {
        public final int batchSize;
        public final long processIntervalMs;
        public final int maxQueueSize;
        public final boolean enableStats;
        
        public EventsConfig() {
            this(DEFAULT_BATCH_SIZE, DEFAULT_PROCESS_INTERVAL_MS, DEFAULT_MAX_QUEUE_SIZE, DEFAULT_ENABLE_STATS);
        }
        
        public EventsConfig(int batchSize, long processIntervalMs, int maxQueueSize, boolean enableStats) {
            this.batchSize = batchSize;
            this.processIntervalMs = processIntervalMs;
            this.maxQueueSize = maxQueueSize;
            this.enableStats = enableStats;
        }
        
        /**
         * Convert to JSON object
         */
        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("batchSize", batchSize);
            json.put("processIntervalMs", processIntervalMs);
            json.put("maxQueueSize", maxQueueSize);
            json.put("enableStats", enableStats);
            return json;
        }
    }
    
    public static class ApiLatenciesConfig {
        public final int batchSize;
        public final long processIntervalMs;
        public final int maxQueueSize;
        public final boolean enableStats;
        
        public ApiLatenciesConfig() {
            this(DEFAULT_BATCH_SIZE, DEFAULT_PROCESS_INTERVAL_MS, DEFAULT_MAX_QUEUE_SIZE, DEFAULT_ENABLE_STATS);
        }
        
        public ApiLatenciesConfig(int batchSize, long processIntervalMs, int maxQueueSize, boolean enableStats) {
            this.batchSize = batchSize;
            this.processIntervalMs = processIntervalMs;
            this.maxQueueSize = maxQueueSize;
            this.enableStats = enableStats;
        }
        
        /**
         * Convert to JSON object
         */
        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("batchSize", batchSize);
            json.put("processIntervalMs", processIntervalMs);
            json.put("maxQueueSize", maxQueueSize);
            json.put("enableStats", enableStats);
            return json;
        }
    }
    
    public static class LogsConfig {
        public final int batchSize;
        public final long processIntervalMs;
        public final int maxQueueSize;
        public final int maxLogsPerFile;
        public final boolean enableStats;
        public final Set<String> whitelistedLogLevels;
        
        public LogsConfig() {
            this(DEFAULT_BATCH_SIZE, DEFAULT_PROCESS_INTERVAL_MS, DEFAULT_MAX_QUEUE_SIZE, DEFAULT_MAX_LOGS_PER_FILE, DEFAULT_ENABLE_STATS, DEFAULT_WHITELISTED_LOG_LEVELS);
        }
        
        public LogsConfig(int batchSize, long processIntervalMs, int maxQueueSize, 
                         int maxLogsPerFile, boolean enableStats, Set<String> whitelistedLogLevels) {
            this.batchSize = batchSize;
            this.processIntervalMs = processIntervalMs;
            this.maxQueueSize = maxQueueSize;
            this.maxLogsPerFile = maxLogsPerFile;
            this.enableStats = enableStats;
            this.whitelistedLogLevels = whitelistedLogLevels != null ? whitelistedLogLevels : DEFAULT_WHITELISTED_LOG_LEVELS;
        }
        
        /**
         * Check if a log level is whitelisted
         */
        public boolean isLogLevelWhitelisted(String logLevel) {
            return whitelistedLogLevels.contains(logLevel);
        }
        
        /**
         * Convert to JSON object
         */
        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("batchSize", batchSize);
            json.put("processIntervalMs", processIntervalMs);
            json.put("maxQueueSize", maxQueueSize);
            json.put("maxLogsPerFile", maxLogsPerFile);
            json.put("enableStats", enableStats);
            
            // Add whitelisted log levels as JSON array
            org.json.JSONArray levelsArray = new org.json.JSONArray();
            for (String level : whitelistedLogLevels) {
                levelsArray.put(level);
            }
            json.put("levels", levelsArray);
            
            return json;
        }
    }
    
    public static class StorageConfig {
        public final String databaseName;
        public final int databaseVersion;
        public final long retentionDays;
        public final boolean enableSQLite;
        public final boolean enableFileStorage;
        
        public StorageConfig() {
            this(DEFAULT_DATABASE_NAME, DEFAULT_DATABASE_VERSION, DEFAULT_RETENTION_DAYS, DEFAULT_ENABLE_SQLITE, DEFAULT_ENABLE_FILE_STORAGE);
        }
        
        public StorageConfig(String databaseName, int databaseVersion, long retentionDays,
                           boolean enableSQLite, boolean enableFileStorage) {
            this.databaseName = databaseName;
            this.databaseVersion = databaseVersion;
            this.retentionDays = retentionDays;
            this.enableSQLite = enableSQLite;
            this.enableFileStorage = enableFileStorage;
        }
        
        /**
         * Convert to JSON object
         */
        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("databaseName", databaseName);
            json.put("databaseVersion", databaseVersion);
            json.put("retentionDays", retentionDays);
            json.put("enableSQLite", enableSQLite);
            json.put("enableFileStorage", enableFileStorage);
            return json;
        }
    }
    
    public static class AppConfig {
        public final boolean enableDebugLogging;
        public final long sessionTimeoutMs;
        public final String environment;
        
        public AppConfig() {
            this(DEFAULT_ENABLE_DEBUG_LOGGING, DEFAULT_SESSION_TIMEOUT_MS, DEFAULT_ENVIRONMENT);
        }
        
        public AppConfig(boolean enableDebugLogging, long sessionTimeoutMs, String environment) {
            this.enableDebugLogging = enableDebugLogging;
            this.sessionTimeoutMs = sessionTimeoutMs;
            this.environment = environment;
        }
        
        /**
         * Convert to JSON object
         */
        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("enableDebugLogging", enableDebugLogging);
            json.put("sessionTimeoutMs", sessionTimeoutMs);
            json.put("environment", environment);
            return json;
        }
    }
    
    public static class DataFlusherConfig {
        public final long flushIntervalMs;
        public final long initialDelayMs;
        public final int batchSize;
        public final boolean enableStats;
        public final boolean enableMetricsFlush;
        public final boolean enableEventsFlush;
        public final boolean enableLogsFlush;
        public final boolean flushOnShutdown;
        public final boolean flushExistingDataOnStartup;
        
        public DataFlusherConfig() {
            this(DEFAULT_FLUSH_INTERVAL_MS, DEFAULT_FLUSH_INITIAL_DELAY_MS, DEFAULT_BATCH_SIZE, DEFAULT_ENABLE_STATS, DEFAULT_ENABLE_METRICS_FLUSH, DEFAULT_ENABLE_EVENTS_FLUSH, DEFAULT_ENABLE_LOGS_FLUSH, DEFAULT_FLUSH_ON_SHUTDOWN, DEFAULT_FLUSH_EXISTING_DATA_ON_STARTUP);
        }
        
        public DataFlusherConfig(long flushIntervalMs, long initialDelayMs, int batchSize, 
                               boolean enableStats, boolean enableMetricsFlush, 
                               boolean enableEventsFlush,
                               boolean enableLogsFlush, boolean flushOnShutdown,
                               boolean flushExistingDataOnStartup) {
            this.flushIntervalMs = flushIntervalMs;
            this.initialDelayMs = initialDelayMs;
            this.batchSize = batchSize;
            this.enableStats = enableStats;
            this.enableMetricsFlush = enableMetricsFlush;
            this.enableEventsFlush = enableEventsFlush;
            this.enableLogsFlush = enableLogsFlush;
            this.flushOnShutdown = flushOnShutdown;
            this.flushExistingDataOnStartup = flushExistingDataOnStartup;
        }
        
        /**
         * Convert to JSON object
         */
        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("flushIntervalMs", flushIntervalMs);
            json.put("initialDelayMs", initialDelayMs);
            json.put("batchSize", batchSize);
            json.put("enableStats", enableStats);
            json.put("enableMetricsFlush", enableMetricsFlush);
            json.put("enableEventsFlush", enableEventsFlush);
            json.put("enableLogsFlush", enableLogsFlush);
            json.put("flushOnShutdown", flushOnShutdown);
            json.put("flushExistingDataOnStartup", flushExistingDataOnStartup);
            return json;
        }
    }
    
    public static class MetricsFetcherConfig {
        public final long collectionIntervalMs;
        public final long initialDelayMs;
        public final boolean enableMemoryMetrics;
        public final boolean enableBatteryMetrics;
        public final boolean enableTemperatureMetrics;
        public final boolean enableStats;
        
        public MetricsFetcherConfig() {
            this(DEFAULT_COLLECTION_INTERVAL_MS, DEFAULT_FETCHER_INITIAL_DELAY_MS, DEFAULT_ENABLE_MEMORY_METRICS, DEFAULT_ENABLE_BATTERY_METRICS, DEFAULT_ENABLE_TEMPERATURE_METRICS, DEFAULT_ENABLE_STATS);
        }
        
        public MetricsFetcherConfig(long collectionIntervalMs, long initialDelayMs, 
                                 boolean enableMemoryMetrics, boolean enableBatteryMetrics, 
                                 boolean enableTemperatureMetrics, boolean enableStats) {
            this.collectionIntervalMs = collectionIntervalMs;
            this.initialDelayMs = initialDelayMs;
            this.enableMemoryMetrics = enableMemoryMetrics;
            this.enableBatteryMetrics = enableBatteryMetrics;
            this.enableTemperatureMetrics = enableTemperatureMetrics;
            this.enableStats = enableStats;
        }
        
        /**
         * Convert to JSON object
         */
        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("collectionIntervalMs", collectionIntervalMs);
            json.put("initialDelayMs", initialDelayMs);
            json.put("enableMemoryMetrics", enableMemoryMetrics);
            json.put("enableBatteryMetrics", enableBatteryMetrics);
            json.put("enableTemperatureMetrics", enableTemperatureMetrics);
            json.put("enableStats", enableStats);
            return json;
        }
    }
} 