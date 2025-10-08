package com.movingtech.appmonitor;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sessionizer - Session enrichment and file writing system with new directory-based mechanism
 * 
 * New Architecture:
 * - Two main directories: storingDirectory and flushingDirectory
 * - Each has subdirectories for each log level: info/, warn/, error/, debug/
 * - Files are created and filled up to batchSize, then new files are created
 * - During flush, entire files are moved and processed, then deleted
 * 
 * Directory Structure:
 * app_monitor_logs/
 * ├── storing/
 * │   ├── info/
 * │   ├── warn/
 * │   ├── error/
 * │   └── debug/
 * └── flushing/
 *     ├── info/
 *     ├── warn/
 *     ├── error/
 *     └── debug/
 */
public class Sessionizer implements ConfigManager.ConfigUpdateListener {

    private static final String TAG = "Sessionizer";
    private static final String LOG_DIR = "app_monitor_logs";
    private static final String STORING_DIR = "storing";
    private static final String FLUSHING_DIR = "flushing";
    private static final String LOG_FILE_EXTENSION = ".log";

    // Dependencies
    private final Context context;
    private final File logDirectory;
    private final File storingDirectory;
    private final File flushingDirectory;
    private final ConfigManager configManager;

    // Configuration
    private volatile ConfigManager.LogsConfig config;

    // Session context
    private String sessionId = "unknown";
    private String userId = "unknown";

    // File management - tracks current active files and their log counts
    private final Map<String, File> activeFiles; // level -> current active file
    private final Map<String, AtomicInteger> fileLogs; // file path -> log count
    private final Map<String, AtomicInteger> fileSequence; // level -> next file sequence number
    
    // Synchronization - separate locks per log level for better concurrency
    private final Map<String, Object> levelLocks; // level -> lock object

    // Statistics (optional)
    private volatile AtomicLong totalLogsSessionized;
    private volatile AtomicLong totalFilesCreated;

    public Sessionizer(Context context) {
        this.context = context;
        this.configManager = ConfigManager.getInstance(context);
        this.config = configManager.getLogsConfig();

        // Initialize directory structure
        this.logDirectory = new File(context.getFilesDir(), LOG_DIR);
        this.storingDirectory = new File(logDirectory, STORING_DIR);
        this.flushingDirectory = new File(logDirectory, FLUSHING_DIR);
        
        // Create directory structure
        createDirectoryStructure();

        // Initialize file management
        this.activeFiles = new ConcurrentHashMap<>();
        this.fileLogs = new ConcurrentHashMap<>();
        this.fileSequence = new ConcurrentHashMap<>();
        this.levelLocks = new ConcurrentHashMap<>();

        // Initialize locks for each log level
        String[] logLevels = {"DEBUG", "INFO", "WARN", "ERROR"};
        for (String level : logLevels) {
            levelLocks.put(level.toLowerCase(), new Object());
            fileSequence.put(level.toLowerCase(), new AtomicInteger(1));
        }

        // Initialize statistics
        updateStatsConfiguration();

        // Register for config updates
        configManager.setConfigUpdateListener(this);

        Log.i(TAG, "Sessionizer initialized with new directory-based file system");
    }

    /**
     * Create the complete directory structure for storing and flushing
     */
    private void createDirectoryStructure() {
        try {
            // Create main directories
            if (!logDirectory.exists()) {
                logDirectory.mkdirs();
            }
            if (!storingDirectory.exists()) {
                storingDirectory.mkdirs();
            }
            if (!flushingDirectory.exists()) {
                flushingDirectory.mkdirs();
            }

            // Create subdirectories for each log level
            String[] logLevels = {"debug", "info", "warn", "error"};
            for (String level : logLevels) {
                File storingLevelDir = new File(storingDirectory, level);
                File flushingLevelDir = new File(flushingDirectory, level);
                
                if (!storingLevelDir.exists()) {
                    storingLevelDir.mkdirs();
                }
                if (!flushingLevelDir.exists()) {
                    flushingLevelDir.mkdirs();
                }
            }
            
            Log.i(TAG, "Created directory structure: " + logDirectory.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error creating directory structure", e);
        }
    }

    /**
     * Set session context (called by AppMonitor)
     */
    public void setSessionContext(String sessionId) {
        this.sessionId = sessionId != null ? sessionId : "unknown";
        Log.i(TAG, "Session context updated: " + this.sessionId);
    }

    /**
     * Set user context (called by AppMonitor)
     */
    public void setUserContext(String userId) {
        this.userId = userId != null ? userId : "unknown";
        Log.i(TAG, "User context updated: " + this.userId);
    }

    /**
     * Process a raw log entry - main entry point from LogsAggregator
     */
    public void processRawLog(LogsAggregator.RawLogEntry rawLog) {
        try {
            // Create enriched log request
            LogRequest enrichedLog = createEnrichedLogRequest(rawLog);
            
            // Write to level-based directory file
            writeToLevelDirectory(enrichedLog);
            
            if (config.enableStats && totalLogsSessionized != null) {
                totalLogsSessionized.incrementAndGet();
            }
            
            Log.v(TAG, "Sessionized and wrote log: " + rawLog.level + " - " + rawLog.message);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing raw log: " + rawLog.level, e);
        }
    }

    /**
     * Create enriched log request from raw log entry
     */
    private LogRequest createEnrichedLogRequest(LogsAggregator.RawLogEntry rawLog) {
        // Start with raw log data
        Map<String, String> enrichedMetadata = new HashMap<>(rawLog.metadata);
        
        // Add device and app metadata if not present
        enrichLogMetadata(enrichedMetadata);
        
        // Create complete log request
        return new LogRequest(
            sessionId,
            userId,
            rawLog.message,
            rawLog.level,
            getOSInfo(),
            rawLog.timestamp,
            new HashMap<>(rawLog.labels),
            rawLog.tag,
            enrichedMetadata
        );
    }

    /**
     * Enrich metadata with device and app information
     */
    private void enrichLogMetadata(Map<String, String> metadata) {

        // Removed not required fields from metadata

        // Add app version if not present
//        if (!metadata.containsKey("app_version")) {
//            metadata.put("app_version", getAppVersion());
//        }

        // Add device info if not present
        if (!metadata.containsKey("device_model")) {
            metadata.put("device_model", android.os.Build.MODEL);
            metadata.put("device_brand", android.os.Build.BRAND);
            metadata.put("device_manufacturer", android.os.Build.MANUFACTURER);
            metadata.put("api_level", String.valueOf(android.os.Build.VERSION.SDK_INT));
//            metadata.put("os_version", android.os.Build.VERSION.RELEASE);
        }

        // Add processing metadata
//        metadata.put("sessionized_at", String.valueOf(System.currentTimeMillis()));
//        metadata.put("processor", "Sessionizer");
        
        // Add log context metadata
//        if (!metadata.containsKey("log_source")) {
//            metadata.put("log_source", "android_app");
//        }
        
        // Add unique log identifier for tracking
        metadata.put("log_id", generateLogId());
    }

    /**
     * Generate a unique log ID for tracking purposes
     */
    private String generateLogId() {
        return sessionId + "_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    /**
     * Write enriched log to level-based directory with new file management
     */
    private void writeToLevelDirectory(LogRequest logRequest) throws IOException, JSONException {
        String level = logRequest.level.toLowerCase();
        Object levelLock = levelLocks.get(level);
        
        synchronized (levelLock) {
            // Get or create active file for this level
            File activeFile = getOrCreateActiveFile(level);
            
            // Check if current file needs rotation (reached batch size)
            String filePath = activeFile.getAbsolutePath();
            AtomicInteger currentLogCount = fileLogs.computeIfAbsent(filePath, k -> new AtomicInteger(0));
            
            if (currentLogCount.get() >= config.maxLogsPerFile) {
                // Create new file for this level
                activeFile = createNewFileForLevel(level);
                currentLogCount = fileLogs.computeIfAbsent(activeFile.getAbsolutePath(), k -> new AtomicInteger(0));
                
                if (config.enableStats && totalFilesCreated != null) {
                    totalFilesCreated.incrementAndGet();
                }
            }

            // Create JSON matching LogsRequest interface
            JSONObject jsonLog = createLogRequestJSON(logRequest);

            // Write to file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(activeFile, true))) {
                writer.write(jsonLog.toString());
                writer.newLine();
                writer.flush();
                currentLogCount.incrementAndGet();
            }
            
            Log.v(TAG, "Wrote to " + activeFile.getName() + ": " + logRequest.level + " - " + logRequest.message + 
                      " (count: " + currentLogCount.get() + "/" + config.maxLogsPerFile + ")");
        }
    }

    /**
     * Get or create active file for a log level
     */
    private File getOrCreateActiveFile(String level) throws IOException {
        File activeFile = activeFiles.get(level);
        
        if (activeFile == null || !activeFile.exists()) {
            activeFile = createNewFileForLevel(level);
        }
        
        return activeFile;
    }

    /**
     * Create new file for a specific log level
     */
    private File createNewFileForLevel(String level) throws IOException {
        File levelDir = new File(storingDirectory, level);
        
        // Ensure level directory exists
        if (!levelDir.exists()) {
            levelDir.mkdirs();
        }
        
        // Generate simple filename without timestamp
        int sequence = fileSequence.get(level).getAndIncrement();
        String fileName = level + "_" + sequence + LOG_FILE_EXTENSION;
        
        File newFile = new File(levelDir, fileName);
        newFile.createNewFile();
        
        // Update active file for this level
        activeFiles.put(level, newFile);
        
        Log.i(TAG, "Created new log file: " + newFile.getAbsolutePath());
        return newFile;
    }

    /**
     * Create JSON object matching LogsRequest interface
     */
    private JSONObject createLogRequestJSON(LogRequest logRequest) throws JSONException {
        JSONObject jsonLog = new JSONObject();
        
        // Core fields matching LogsRequest interface
        jsonLog.put("sessionId", logRequest.sessionId);
        jsonLog.put("userId", logRequest.userId);
        jsonLog.put("message", logRequest.message);
        jsonLog.put("level", logRequest.level);
        jsonLog.put("os", logRequest.os);
        jsonLog.put("timestamp", logRequest.timestamp);
        
        // Labels (required field)
        JSONObject labelsJson = new JSONObject();
        if (logRequest.labels != null && !logRequest.labels.isEmpty()) {
            for (Map.Entry<String, String> entry : logRequest.labels.entrySet()) {
                labelsJson.put(entry.getKey(), entry.getValue());
            }
        }
        jsonLog.put("labels", labelsJson);
        
        // Optional fields
        if (logRequest.tag != null && !logRequest.tag.trim().isEmpty()) {
            jsonLog.put("tag", logRequest.tag);
        }
        
        if (logRequest.metadata != null && !logRequest.metadata.isEmpty()) {
            JSONObject metadataJson = new JSONObject();
            for (Map.Entry<String, String> entry : logRequest.metadata.entrySet()) {
                metadataJson.put(entry.getKey(), entry.getValue());
            }
            jsonLog.put("metadata", metadataJson);
        }
        
        return jsonLog;
    }

    /**
     * Get storing directory for external access (used by FileStorageProvider)
     */
    public File getStoringDirectory() {
        return storingDirectory;
    }

    /**
     * Get flushing directory for external access (used by FileStorageProvider)
     */
    public File getFlushingDirectory() {
        return flushingDirectory;
    }

    /**
     * Force close current active files (useful for testing or shutdown)
     */
    public void closeActiveFiles() {
        synchronized (activeFiles) {
            activeFiles.clear();
            Log.i(TAG, "Closed all active files");
        }
    }

    /**
     * Get processing statistics
     */
    public SessionizerStats getStats() {
        if (!config.enableStats) {
            return new SessionizerStats(0, 0, activeFiles.size(), false);
        }
        
        return new SessionizerStats(
            totalLogsSessionized != null ? totalLogsSessionized.get() : 0,
            totalFilesCreated != null ? totalFilesCreated.get() : 0,
            activeFiles.size(),
            true
        );
    }

    /**
     * Get file counts by level (for debugging)
     */
    public Map<String, Integer> getActiveFilesByLevel() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, File> entry : activeFiles.entrySet()) {
            String filePath = entry.getValue().getAbsolutePath();
            AtomicInteger logCount = fileLogs.get(filePath);
            counts.put(entry.getKey(), logCount != null ? logCount.get() : 0);
        }
        return counts;
    }

    // Helper methods
    private String getOSInfo() {
        return "ANDROID";
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

    // ConfigUpdateListener implementation
    @Override
    public void onConfigUpdated(JSONObject newConfig) {
        ConfigManager.LogsConfig newLogsConfig = configManager.getLogsConfig();
        this.config = newLogsConfig;
        updateStatsConfiguration();
        
        Log.i(TAG, "Sessionizer configuration updated - maxLogsPerFile: " + config.maxLogsPerFile);
    }

    @Override
    public void onConfigError(String error) {
        Log.e(TAG, "Configuration error: " + error);
    }

    private void updateStatsConfiguration() {
        if (config.enableStats) {
            if (totalLogsSessionized == null) {
                totalLogsSessionized = new AtomicLong(0);
            }
            if (totalFilesCreated == null) {
                totalFilesCreated = new AtomicLong(0);
            }
        } else {
            totalLogsSessionized = null;
            totalFilesCreated = null;
        }
    }

    // Inner classes

    /**
     * Complete log request matching LogsRequest interface
     */
    public static class LogRequest {
        public final String sessionId;
        public final String userId;
        public final String message;
        public final String level;
        public final String os;
        public final long timestamp;
        public final Map<String, String> labels;
        public final String tag;
        public final Map<String, String> metadata;

        public LogRequest(String sessionId, String userId, String message, String level,
                         String os, long timestamp, Map<String, String> labels, 
                         String tag, Map<String, String> metadata) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.message = message;
            this.level = level;
            this.os = os;
            this.timestamp = timestamp;
            this.labels = labels;
            this.tag = tag;
            this.metadata = metadata;
        }

        @Override
        public String toString() {
            return String.format("LogRequest{level='%s', message='%s', user='%s', session='%s', timestamp=%d}", 
                level, message, userId, sessionId, timestamp);
        }
    }

    /**
     * Sessionizer processing statistics
     */
    public static class SessionizerStats {
        public final long totalSessionized;
        public final long totalFilesCreated;
        public final int activeFiles;
        public final boolean statsEnabled;

        public SessionizerStats(long totalSessionized, long totalFilesCreated, int activeFiles, boolean statsEnabled) {
            this.totalSessionized = totalSessionized;
            this.totalFilesCreated = totalFilesCreated;
            this.activeFiles = activeFiles;
            this.statsEnabled = statsEnabled;
        }

        public String getFormattedStats() {
            StringBuilder sb = new StringBuilder();
            sb.append("Sessionizer Stats:\n");
            if (statsEnabled) {
                sb.append("Total Sessionized: ").append(totalSessionized).append("\n");
                sb.append("Total Files Created: ").append(totalFilesCreated).append("\n");
            } else {
                sb.append("Statistics disabled\n");
            }
            sb.append("Active Files: ").append(activeFiles).append("\n");
            return sb.toString();
        }
    }
} 