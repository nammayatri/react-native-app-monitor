package com.movingtech.appmonitor.storage;

import android.content.Context;
import android.util.Log;

import com.movingtech.appmonitor.Sessionizer;

import org.json.JSONArray;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * StorageManager coordinates between SQLite and File storage providers
 * Updated for new directory-based log architecture
 */
public class StorageManager implements StorageProvider {

    private static final String TAG = "StorageManager";
    
    private SQLiteStorageProvider sqliteProvider;
    private FileStorageProvider fileProvider;
    private Sessionizer sessionizer; // New: Direct connection to Sessionizer

    public StorageManager(Context context) {
        this(context, "app_monitor.db", 1);
    }

    public StorageManager(Context context, String databaseName, int databaseVersion) {
        sqliteProvider = new SQLiteStorageProvider(context, databaseName, databaseVersion);
        fileProvider = new FileStorageProvider(context);
        
        // Initialize Sessionizer and connect directories
        sessionizer = new Sessionizer(context);
        fileProvider.setDirectories(sessionizer.getStoringDirectory(), sessionizer.getFlushingDirectory());
        
        Log.i(TAG, "StorageManager initialized with new directory-based architecture");
    }

    @Override
    public boolean storeMetric(long timestamp, String metricName, Object value, Map<String, Object> metadata, String sessionId,
                               String userId) {
        // Route metrics to SQLite storage
        return sqliteProvider.storeMetric(timestamp, metricName, value, metadata, sessionId, userId);
    }

    @Override
    public boolean storeEvent(long timestamp, String eventType, String eventName, Map<String, Object> eventData, String sessionId, String userId) {
        // Route events to SQLite storage
        return sqliteProvider.storeEvent(timestamp, eventType, eventName, eventData, sessionId, userId);
    }

    /**
     * Store API latency data
     */
    public boolean storeApiLatency(long timestamp, String endpoint, String method, long latency, int statusCode, String sessionId,
                                   String userId) {
        // Route API latencies to SQLite storage
        return sqliteProvider.storeApiLatency(timestamp, endpoint, method, latency, statusCode, sessionId, userId);
    }

    @Override
    public boolean storeLog(long timestamp, String logLevel, String message, String tag) {
        // Route logs to Sessionizer for new architecture
        // Note: This method is deprecated in favor of LogsAggregator -> Sessionizer flow
        Log.w(TAG, "storeLog called directly - logs should be processed through LogsAggregator -> Sessionizer");
        return false;
    }

    @Override
    public List<Map<String, Object>> getMetrics(long startTime, long endTime, String metricName) {
        return sqliteProvider.getMetrics(startTime, endTime, metricName);
    }

    @Override
    public List<Map<String, Object>> getEvents(long startTime, long endTime, String eventType) {
        return sqliteProvider.getEvents(startTime, endTime, eventType);
    }

    @Override
    public List<String> getLogs(long startTime, long endTime, String logLevel) {
        // Legacy method - not used in new architecture
        Log.w(TAG, "getLogs called - use new file-based methods instead");
        return fileProvider.getLogs(startTime, endTime, logLevel);
    }

    // Additional methods for DataFlusher with new architecture
    
    /**
     * Get metrics with limit for batch processing
     */
    public List<Map<String, Object>> getMetrics(long startTime, long endTime, int limit) {
        return sqliteProvider.getMetrics(startTime, endTime, null, limit);
    }

    /**
     * Get events with limit for batch processing
     */
    public List<Map<String, Object>> getEvents(long startTime, long endTime, int limit) {
        return sqliteProvider.getEvents(startTime, endTime, null, limit);
    }

    /**
     * Get API latencies with limit for batch processing
     */
    public List<Map<String, Object>> getApiLatencies(long startTime, long endTime, int limit) {
        return sqliteProvider.getApiLatencies(startTime, endTime, null, limit);
    }

    /**
     * Get logs with limit for batch processing (legacy method)
     */
    public List<Map<String, Object>> getLogs(long startTime, long endTime, int limit) {
        // Legacy method - not used in new architecture
        Log.w(TAG, "getLogs with limit called - use new file-based methods instead");
        return fileProvider.getLogsAsObjects(startTime, endTime, null, limit);
    }

    // Delete methods
    
    /**
     * Delete metrics in time range
     */
    public boolean deleteMetrics(long startTime, long endTime) {
        return sqliteProvider.deleteMetrics(startTime, endTime);
    }

    /**
     * Delete events in time range
     */
    public boolean deleteEvents(long startTime, long endTime) {
        return sqliteProvider.deleteEvents(startTime, endTime);
    }

    /**
     * Delete API latencies in time range
     */
    public boolean deleteApiLatencies(long startTime, long endTime) {
        return sqliteProvider.deleteApiLatencies(startTime, endTime);
    }

    /**
     * Delete logs in time range (legacy method)
     */
    public boolean deleteLogs(long startTime, long endTime) {
        // Legacy method - not used in new architecture
        Log.w(TAG, "deleteLogs called - use new file-based methods instead");
        return fileProvider.deleteLogs(startTime, endTime);
    }

    // New file-based log methods for the new architecture

    /**
     * Prepare files for flushing by moving from storing to flushing directory
     * This is the core method for the new flush mechanism
     */
    public FileStorageProvider.FlushPrepResult prepareFilesForFlushing(Set<String> whitelistedLevels) {
        return fileProvider.prepareFilesForFlushing(whitelistedLevels);
    }

    /**
     * Get files ready for flushing (for startup recovery)
     */
    public FileStorageProvider.FlushPrepResult getFilesReadyForFlushing(Set<String> whitelistedLevels) {
        return fileProvider.getFilesReadyForFlushing(whitelistedLevels);
    }

    /**
     * Read a specific file as JSONArray for backend transmission
     */
    public JSONArray readFileAsJSONArray(java.io.File logFile) {
        return fileProvider.readFileAsJSONArray(logFile);
    }

    /**
     * Delete a processed file after successful transmission
     */
    public boolean deleteProcessedFile(java.io.File processedFile) {
        return fileProvider.deleteProcessedFile(processedFile);
    }

    /**
     * Get whitelisted logs as JSONArray (for compatibility with existing code)
     */
    public JSONArray getWhitelistedLogsAsJSON(Set<String> whitelistedLevels, int limit) {
        return fileProvider.getWhitelistedLogsAsJSON(whitelistedLevels, limit);
    }

    /**
     * Get whitelisted logs as objects (for compatibility)
     */
    public List<Map<String, Object>> getWhitelistedLogs(Set<String> whitelistedLevels, int limit) {
        // Convert JSONArray to List<Map<String, Object>> for compatibility
        JSONArray jsonLogs = fileProvider.getWhitelistedLogsAsJSON(whitelistedLevels, limit);
        List<Map<String, Object>> logs = new java.util.ArrayList<>();
        
        for (int i = 0; i < jsonLogs.length(); i++) {
            try {
                org.json.JSONObject jsonLog = jsonLogs.getJSONObject(i);
                Map<String, Object> logMap = new java.util.HashMap<>();
                
                // Convert JSONObject to Map
                java.util.Iterator<String> keys = jsonLog.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    logMap.put(key, jsonLog.get(key));
                }
                logs.add(logMap);
            } catch (org.json.JSONException e) {
                Log.w(TAG, "Error converting log to map", e);
            }
        }
        
        return logs;
    }

    /**
     * Delete non-whitelisted logs
     */
    public int deleteNonWhitelistedLogs(Set<String> whitelistedLevels) {
        return fileProvider.deleteNonWhitelistedLogs(whitelistedLevels);
    }

    /**
     * Delete sent logs (deprecated in new architecture - files are deleted entirely)
     */
    public int deleteSentLogs(JSONArray sentLogs, Set<String> whitelistedLevels) {
        Log.w(TAG, "deleteSentLogs called - in new architecture, entire files are deleted instead");
        return 0; // Not applicable in new architecture
    }

    @Override
    public boolean initialize() {
        boolean sqliteOk = sqliteProvider.initialize();
        boolean fileOk = fileProvider.initialize();
        
        if (sqliteOk && fileOk) {
            Log.i(TAG, "StorageManager initialized successfully");
            return true;
        } else {
            Log.e(TAG, "StorageManager initialization failed - SQLite: " + sqliteOk + ", File: " + fileOk);
            return false;
        }
    }

    @Override
    public void cleanup() {
        try {
            sqliteProvider.cleanup();
            fileProvider.cleanup();
            Log.i(TAG, "StorageManager cleanup completed");
        } catch (Exception e) {
            Log.e(TAG, "Error during StorageManager cleanup", e);
        }
    }

    @Override
    public int cleanupOldData(int retentionDays) {
        int sqliteDeleted = sqliteProvider.cleanupOldData(retentionDays);
        int fileDeleted = fileProvider.cleanupOldData(retentionDays);
        
        Log.i(TAG, "Cleaned up old data - SQLite: " + sqliteDeleted + " records, Files: " + fileDeleted + " files");
        return sqliteDeleted + fileDeleted;
    }

    // Access to underlying providers

    /**
     * Get SQLite provider for direct access
     */
    public SQLiteStorageProvider getSqliteProvider() {
        return sqliteProvider;
    }

    /**
     * Get File provider for direct access
     */
    public FileStorageProvider getFileProvider() {
        return fileProvider;
    }

    /**
     * Get Sessionizer for direct access
     */
    public Sessionizer getSessionizer() {
        return sessionizer;
    }

    /**
     * Get storage statistics
     */
    public StorageStats getStorageStats() {
        return new StorageStats(this);
    }

    public static class StorageStats {
        private final StorageManager manager;

        public StorageStats(StorageManager manager) {
            this.manager = manager;
        }

        public long getTotalLogSize() {
            return manager.fileProvider.getStorageStats().storingSize + 
                   manager.fileProvider.getStorageStats().flushingSize;
        }

        public int getLogFileCount() {
            return manager.fileProvider.getStorageStats().storingFiles + 
                   manager.fileProvider.getStorageStats().flushingFiles;
        }

        public String getFormattedStats() {
            FileStorageProvider.StorageStats fileStats = manager.fileProvider.getStorageStats();
            return String.format("Storage Manager Stats:\n" +
                               "=== File Storage ===\n" +
                               "%s\n" +
                               "=== SQLite Storage ===\n" +
                               "Database: %s\n" +
                               "Initialized: %s",
                               fileStats.getFormattedStats(),
                               manager.sqliteProvider.getDatabaseName(),
                               "Yes");
        }
    }

    /**
     * Perform health check on all storage providers
     */
    public HealthCheckResult performHealthCheck() {
        HealthCheckResult result = new HealthCheckResult();
        
        try {
            // Check SQLite provider
            result.sqliteHealthy = sqliteProvider.initialize();
            if (!result.sqliteHealthy) {
                result.sqliteError = "SQLite provider initialization failed";
            }
        } catch (Exception e) {
            result.sqliteHealthy = false;
            result.sqliteError = "SQLite provider error: " + e.getMessage();
        }
        
        try {
            // Check File provider
            result.fileStorageHealthy = fileProvider.initialize();
            if (!result.fileStorageHealthy) {
                result.fileStorageError = "File provider initialization failed";
            }
        } catch (Exception e) {
            result.fileStorageHealthy = false;
            result.fileStorageError = "File provider error: " + e.getMessage();
        }
        
        result.overallHealthy = result.sqliteHealthy && result.fileStorageHealthy;
        
        Log.i(TAG, "Health check completed - Overall: " + result.overallHealthy + 
                   ", SQLite: " + result.sqliteHealthy + ", File: " + result.fileStorageHealthy);
        
        return result;
    }

    public static class HealthCheckResult {
        public boolean overallHealthy;
        public boolean sqliteHealthy;
        public boolean fileStorageHealthy;
        public String sqliteError;
        public String fileStorageError;

        public String getReport() {
            StringBuilder report = new StringBuilder();
            report.append("=== Storage Health Check Report ===\n");
            report.append("Overall Status: ").append(overallHealthy ? "HEALTHY" : "UNHEALTHY").append("\n");
            report.append("SQLite Storage: ").append(sqliteHealthy ? "OK" : "FAILED").append("\n");
            if (sqliteError != null) {
                report.append("  Error: ").append(sqliteError).append("\n");
            }
            report.append("File Storage: ").append(fileStorageHealthy ? "OK" : "FAILED").append("\n");
            if (fileStorageError != null) {
                report.append("  Error: ").append(fileStorageError).append("\n");
            }
            return report.toString();
        }
    }

    // Batch processing methods for DataFlusher

    /**
     * Get metrics with batch size limit
     */
    public List<Map<String, Object>> getMetrics(int limit) {
        return sqliteProvider.getMetrics(limit);
    }

    /**
     * Delete specific metrics by their data
     */
    public boolean deleteMetrics(List<Map<String, Object>> metrics) {
        return sqliteProvider.deleteMetrics(metrics);
    }

    /**
     * Get events with batch size limit
     */
    public List<Map<String, Object>> getEvents(int limit) {
        return sqliteProvider.getEvents(limit);
    }

    /**
     * Delete specific events by their data
     */
    public boolean deleteEvents(List<Map<String, Object>> events) {
        return sqliteProvider.deleteEvents(events);
    }

    /**
     * Get API latencies with batch size limit
     */
    public List<Map<String, Object>> getApiLatencies(int limit) {
        return sqliteProvider.getApiLatencies(limit);
    }

    /**
     * Delete specific API latencies by their data
     */
    public boolean deleteApiLatencies(List<Map<String, Object>> apiLatencies) {
        return sqliteProvider.deleteApiLatencies(apiLatencies);
    }

    // Recovery methods for startup data processing

    /**
     * Get maximum metrics ID for recovery boundary
     */
    public long getMaxMetricsId() {
        return sqliteProvider.getMaxMetricsId();
    }

    /**
     * Get maximum events ID for recovery boundary
     */
    public long getMaxEventsId() {
        return sqliteProvider.getMaxEventsId();
    }

    /**
     * Get maximum API latencies ID for recovery boundary
     */
    public long getMaxApiLatenciesId() {
        return sqliteProvider.getMaxApiLatenciesId();
    }

    /**
     * Get metrics up to specific ID for recovery processing
     */
    public List<Map<String, Object>> getMetricsUpToId(int limit, long maxId) {
        return sqliteProvider.getMetricsUpToId(limit, maxId);
    }

    /**
     * Get events up to specific ID for recovery processing
     */
    public List<Map<String, Object>> getEventsUpToId(int limit, long maxId) {
        return sqliteProvider.getEventsUpToId(limit, maxId);
    }

    /**
     * Get API latencies up to specific ID for recovery processing
     */
    public List<Map<String, Object>> getApiLatenciesUpToId(int limit, long maxId) {
        return sqliteProvider.getApiLatenciesUpToId(limit, maxId);
    }
} 