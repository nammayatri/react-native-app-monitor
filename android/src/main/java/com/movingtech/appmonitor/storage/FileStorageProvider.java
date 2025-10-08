package com.movingtech.appmonitor.storage;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * File-based implementation of StorageProvider with new directory-based architecture
 * 
 * New Architecture:
 * - Uses two-directory system: storing/ and flushing/
 * - Each directory has subdirectories for log levels: info/, warn/, error/, debug/
 * - Files are moved atomically from storing to flushing during flush operations
 * - Entire files are processed and deleted (not individual log entries)
 * 
 * Directory Structure:
 * app_monitor_logs/
 * ├── storing/
 * │   ├── info/
 * │   │   ├── info_2024-01-15_14-30-45_1.log
 * │   │   └── info_2024-01-15_14-35-12_2.log
 * │   ├── warn/
 * │   ├── error/
 * │   └── debug/
 * └── flushing/
 *     ├── info/
 *     ├── warn/
 *     ├── error/
 *     └── debug/
 */
public class FileStorageProvider implements StorageProvider {

    private static final String TAG = "FileStorageProvider";
    
    // Directory references (will be set from Sessionizer)
    private File storingDirectory;
    private File flushingDirectory;
    
    // Synchronization for move operations
    private final Object flushLock = new Object();

    public FileStorageProvider(Context context) {
        // Directory setup will be done through setDirectories method
        Log.i(TAG, "FileStorageProvider initialized with new directory-based architecture");
    }

    /**
     * Set directory references from Sessionizer
     * This allows FileStorageProvider to access the same directory structure
     */
    public void setDirectories(File storingDirectory, File flushingDirectory) {
        this.storingDirectory = storingDirectory;
        this.flushingDirectory = flushingDirectory;
        Log.i(TAG, "Directory references set - storing: " + storingDirectory.getAbsolutePath() + 
                   ", flushing: " + flushingDirectory.getAbsolutePath());
    }

    @Override
    public boolean storeMetric(long timestamp, String metricName, Object value, Map<String, Object> metadata, String sessionId,
                               String userId) {
        // File provider doesn't handle metrics - delegated to SQLite provider
        Log.w(TAG, "storeMetric called on File provider - metrics should be handled by SQLite provider");
        return false;
    }

    @Override
    public boolean storeEvent(long timestamp, String eventType, String eventName, Map<String, Object> eventData, String sessionId,
                              String userId) {
        // File provider doesn't handle events - delegated to SQLite provider
        Log.w(TAG, "storeEvent called on File provider - events should be handled by SQLite provider");
        return false;
    }

    @Override
    public boolean storeLog(long timestamp, String logLevel, String message, String tag) {
        // This method is deprecated in favor of the new Sessionizer-based storage
        Log.w(TAG, "storeLog called - logs should be handled by Sessionizer");
        return false;
    }

    @Override
    public List<Map<String, Object>> getMetrics(long startTime, long endTime, String metricName) {
        // File provider doesn't handle metrics
        Log.w(TAG, "getMetrics called on File provider - metrics should be handled by SQLite provider");
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> getEvents(long startTime, long endTime, String eventType) {
        // File provider doesn't handle events
        Log.w(TAG, "getEvents called on File provider - events should be handled by SQLite provider");
        return new ArrayList<>();
    }

    @Override
    public List<String> getLogs(long startTime, long endTime, String logLevel) {
        // Legacy method - not used in new architecture
        Log.w(TAG, "getLogs called - use new file-based methods instead");
        return new ArrayList<>();
    }

    /**
     * Move files from storing to flushing directory for processing
     * This is the core operation for the new flush mechanism
     * 
     * @param whitelistedLevels Set of log levels to move
     * @return FlushPrepResult containing moved files information
     */
    public FlushPrepResult prepareFilesForFlushing(Set<String> whitelistedLevels) {
        synchronized (flushLock) {
            FlushPrepResult result = new FlushPrepResult();
            
            if (storingDirectory == null || flushingDirectory == null) {
                Log.e(TAG, "Directories not set - cannot prepare files for flushing");
                return result;
            }
            
            try {
                for (String level : whitelistedLevels) {
                    File storingLevelDir = new File(storingDirectory, level.toLowerCase());
                    File flushingLevelDir = new File(flushingDirectory, level.toLowerCase());
                    
                    // Ensure flushing level directory exists
                    if (!flushingLevelDir.exists()) {
                        flushingLevelDir.mkdirs();
                    }
                    
                    // Get all files in storing level directory
                    File[] filesToMove = storingLevelDir.listFiles((dir, name) -> name.endsWith(".log"));
                    
                    if (filesToMove != null && filesToMove.length > 0) {
                        List<File> movedFiles = new ArrayList<>();
                        
                        for (File file : filesToMove) {
                            // Move file to flushing directory
                            File targetFile = new File(flushingLevelDir, file.getName());
                            
                            if (file.renameTo(targetFile)) {
                                movedFiles.add(targetFile);
                                Log.d(TAG, "Moved file: " + file.getName() + " to flushing/" + level.toLowerCase());
                            } else {
                                Log.e(TAG, "Failed to move file: " + file.getName());
                            }
                        }
                        
                        if (!movedFiles.isEmpty()) {
                            result.addLevelFiles(level.toLowerCase(), movedFiles);
                        }
                    }
                }
                
                Log.i(TAG, "Prepared " + result.getTotalFileCount() + " files for flushing across " + 
                           result.getLevelCount() + " log levels");
                
            } catch (Exception e) {
                Log.e(TAG, "Error preparing files for flushing", e);
            }
            
            return result;
        }
    }

    /**
     * Get all files ready for flushing (already in flushing directory)
     * Used for startup recovery to process any files left in flushing directory
     */
    public FlushPrepResult getFilesReadyForFlushing(Set<String> whitelistedLevels) {
        FlushPrepResult result = new FlushPrepResult();
        
        if (flushingDirectory == null) {
            Log.e(TAG, "Flushing directory not set");
            return result;
        }
        
        try {
            for (String level : whitelistedLevels) {
                File flushingLevelDir = new File(flushingDirectory, level.toLowerCase());
                
                if (flushingLevelDir.exists()) {
                    File[] readyFiles = flushingLevelDir.listFiles((dir, name) -> name.endsWith(".log"));
                    
                    if (readyFiles != null && readyFiles.length > 0) {
                        List<File> filesList = Arrays.asList(readyFiles);
                        result.addLevelFiles(level.toLowerCase(), filesList);
                    }
                }
            }
            
            Log.i(TAG, "Found " + result.getTotalFileCount() + " files ready for flushing");
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting files ready for flushing", e);
        }
        
        return result;
    }

    /**
     * Read all logs from a specific file as JSONArray
     * Used for sending file contents to backend
     */
    public JSONArray readFileAsJSONArray(File logFile) {
        JSONArray logs = new JSONArray();
        
        if (!logFile.exists() || !logFile.isFile()) {
            Log.w(TAG, "File does not exist or is not a file: " + logFile.getAbsolutePath());
            return logs;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    try {
                        JSONObject logJson = new JSONObject(line);
                        logs.put(logJson);
                    } catch (JSONException e) {
                        Log.w(TAG, "Skipping invalid JSON line in file " + logFile.getName() + ": " + line);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + logFile.getAbsolutePath(), e);
        }
        
        Log.d(TAG, "Read " + logs.length() + " logs from file: " + logFile.getName());
        return logs;
    }

    /**
     * Delete a processed file after successful transmission to backend
     */
    public boolean deleteProcessedFile(File processedFile) {
        if (processedFile.exists()) {
            boolean deleted = processedFile.delete();
            if (deleted) {
                Log.d(TAG, "Deleted processed file: " + processedFile.getName());
            } else {
                Log.e(TAG, "Failed to delete processed file: " + processedFile.getName());
            }
            return deleted;
        }
        return true; // File doesn't exist, consider it deleted
    }

    /**
     * Get whitelisted logs as JSONArray from storing directory
     * This is used for compatibility with existing flush mechanisms
     */
    public JSONArray getWhitelistedLogsAsJSON(Set<String> whitelistedLevels, int limit) {
        JSONArray logs = new JSONArray();
        int logCount = 0;
        
        if (storingDirectory == null) {
            Log.e(TAG, "Storing directory not set");
            return logs;
        }
        
        try {
            for (String level : whitelistedLevels) {
                if (limit != -1 && logCount >= limit) {
                    break;
                }
                
                File levelDir = new File(storingDirectory, level.toLowerCase());
                if (levelDir.exists()) {
                    File[] levelFiles = levelDir.listFiles((dir, name) -> name.endsWith(".log"));
                    
                    if (levelFiles != null) {
                        for (File file : levelFiles) {
                            if (limit != -1 && logCount >= limit) {
                                break;
                            }
                            
                            JSONArray fileLogs = readFileAsJSONArray(file);
                            for (int i = 0; i < fileLogs.length() && (limit == -1 || logCount < limit); i++) {
                                try {
                                    logs.put(fileLogs.getJSONObject(i));
                                    logCount++;
                                } catch (JSONException e) {
                                    Log.w(TAG, "Error adding log to array", e);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting whitelisted logs", e);
        }
        
        Log.i(TAG, "Retrieved " + logs.length() + " logs from storing directory");
        return logs;
    }

    /**
     * Delete non-whitelisted level directories
     * More efficient than individual file operations
     */
    public int deleteNonWhitelistedLogs(Set<String> whitelistedLevels) {
        int deletedFiles = 0;
        
        if (storingDirectory == null) {
            Log.e(TAG, "Storing directory not set");
            return 0;
        }
        
        try {
            File[] levelDirs = storingDirectory.listFiles(File::isDirectory);
            
            if (levelDirs != null) {
                for (File levelDir : levelDirs) {
                    String levelName = levelDir.getName().toLowerCase();
                    
                    if (!whitelistedLevels.contains(levelName.toUpperCase()) && !whitelistedLevels.contains(levelName)) {
                        // Delete all files in non-whitelisted level directory
                        File[] filesToDelete = levelDir.listFiles((dir, name) -> name.endsWith(".log"));
                        
                        if (filesToDelete != null) {
                            for (File file : filesToDelete) {
                                if (file.delete()) {
                                    deletedFiles++;
                                    Log.d(TAG, "Deleted non-whitelisted log file: " + file.getName());
                                }
                            }
                        }
                        
                        // Delete empty directory
                        if (levelDir.listFiles() == null || levelDir.listFiles().length == 0) {
                            levelDir.delete();
                            Log.d(TAG, "Deleted empty non-whitelisted directory: " + levelName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting non-whitelisted logs", e);
        }
        
        Log.i(TAG, "Deleted " + deletedFiles + " non-whitelisted log files");
        return deletedFiles;
    }

    /**
     * Clean up empty directories in both storing and flushing
     */
    public void cleanupEmptyDirectories() {
        cleanupEmptyDirectoriesInPath(storingDirectory);
        cleanupEmptyDirectoriesInPath(flushingDirectory);
    }
    
    private void cleanupEmptyDirectoriesInPath(File parentDir) {
        if (parentDir == null || !parentDir.exists()) {
            return;
        }
        
        try {
            File[] subdirs = parentDir.listFiles(File::isDirectory);
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    File[] files = subdir.listFiles();
                    if (files == null || files.length == 0) {
                        if (subdir.delete()) {
                            Log.d(TAG, "Deleted empty directory: " + subdir.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up empty directories", e);
        }
    }

    @Override
    public boolean initialize() {
        // Initialization is handled by Sessionizer
        Log.i(TAG, "FileStorageProvider initialization - directory setup handled by Sessionizer");
        return true;
    }

    @Override
    public void cleanup() {
        cleanupEmptyDirectories();
        Log.i(TAG, "FileStorageProvider cleanup completed");
    }

    @Override
    public int cleanupOldData(int retentionDays) {
        // Clean up old files in both directories
        int deletedFiles = 0;
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
        
        deletedFiles += cleanupOldFilesInDirectory(storingDirectory, cutoffTime);
        deletedFiles += cleanupOldFilesInDirectory(flushingDirectory, cutoffTime);
        
        Log.i(TAG, "Cleaned up " + deletedFiles + " old log files");
        return deletedFiles;
    }
    
    private int cleanupOldFilesInDirectory(File directory, long cutoffTime) {
        int deletedFiles = 0;
        
        if (directory == null || !directory.exists()) {
            return 0;
        }
        
        try {
            File[] levelDirs = directory.listFiles(File::isDirectory);
            if (levelDirs != null) {
                for (File levelDir : levelDirs) {
                    File[] logFiles = levelDir.listFiles((dir, name) -> name.endsWith(".log"));
                    if (logFiles != null) {
                        for (File logFile : logFiles) {
                            if (logFile.lastModified() < cutoffTime) {
                                if (logFile.delete()) {
                                    deletedFiles++;
                                    Log.d(TAG, "Deleted old log file: " + logFile.getName());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up old files in directory: " + directory.getAbsolutePath(), e);
        }
        
        return deletedFiles;
    }

    /**
     * Get storage statistics for the new architecture
     */
    public StorageStats getStorageStats() {
        return new StorageStats();
    }

    /**
     * Storage statistics for the new directory-based architecture
     */
    public class StorageStats {
        public final int storingFiles;
        public final int flushingFiles;
        public final long storingSize;
        public final long flushingSize;
        
        public StorageStats() {
            this.storingFiles = countFilesInDirectory(storingDirectory);
            this.flushingFiles = countFilesInDirectory(flushingDirectory);
            this.storingSize = getDirectorySize(storingDirectory);
            this.flushingSize = getDirectorySize(flushingDirectory);
        }
        
        private int countFilesInDirectory(File directory) {
            if (directory == null || !directory.exists()) {
                return 0;
            }
            
            int count = 0;
            File[] levelDirs = directory.listFiles(File::isDirectory);
            if (levelDirs != null) {
                for (File levelDir : levelDirs) {
                    File[] logFiles = levelDir.listFiles((dir, name) -> name.endsWith(".log"));
                    if (logFiles != null) {
                        count += logFiles.length;
                    }
                }
            }
            return count;
        }
        
        private long getDirectorySize(File directory) {
            if (directory == null || !directory.exists()) {
                return 0;
            }
            
            long size = 0;
            File[] levelDirs = directory.listFiles(File::isDirectory);
            if (levelDirs != null) {
                for (File levelDir : levelDirs) {
                    File[] logFiles = levelDir.listFiles((dir, name) -> name.endsWith(".log"));
                    if (logFiles != null) {
                        for (File file : logFiles) {
                            size += file.length();
                        }
                    }
                }
            }
            return size;
        }
        
        public String getFormattedStats() {
            return String.format("FileStorage Stats:\n" +
                               "Storing Files: %d (%.2f KB)\n" +
                               "Flushing Files: %d (%.2f KB)\n" +
                               "Total Files: %d (%.2f KB)",
                               storingFiles, storingSize / 1024.0,
                               flushingFiles, flushingSize / 1024.0,
                               storingFiles + flushingFiles, (storingSize + flushingSize) / 1024.0);
        }
    }

    /**
     * Result class for file preparation operations
     */
    public static class FlushPrepResult {
        private final java.util.Map<String, List<File>> levelFiles;
        private final AtomicInteger totalFiles;
        
        public FlushPrepResult() {
            this.levelFiles = new java.util.HashMap<>();
            this.totalFiles = new AtomicInteger(0);
        }
        
        public void addLevelFiles(String level, List<File> files) {
            levelFiles.put(level, files);
            totalFiles.addAndGet(files.size());
        }
        
        public List<File> getFilesForLevel(String level) {
            return levelFiles.getOrDefault(level, new ArrayList<>());
        }
        
        public Set<String> getLevels() {
            return levelFiles.keySet();
        }
        
        public int getTotalFileCount() {
            return totalFiles.get();
        }
        
        public int getLevelCount() {
            return levelFiles.size();
        }
        
        public boolean hasFiles() {
            return totalFiles.get() > 0;
        }
        
        @Override
        public String toString() {
            return String.format("FlushPrepResult{totalFiles=%d, levels=%d, levelFiles=%s}", 
                               totalFiles.get(), levelFiles.size(), levelFiles.keySet());
        }
    }

    /**
     * Get logs as objects (legacy method for compatibility)
     */
    public List<Map<String, Object>> getLogsAsObjects(long startTime, long endTime, String logLevel, int limit) {
        Log.w(TAG, "getLogsAsObjects called - use new file-based methods instead");
        List<Map<String, Object>> logs = new ArrayList<>();
        // In the new architecture, this is not used but we provide empty implementation for compatibility
        return logs;
    }

    /**
     * Delete logs in time range (legacy method for compatibility)
     */
    public boolean deleteLogs(long startTime, long endTime) {
        Log.w(TAG, "deleteLogs called - use new file-based methods instead");
        // In the new architecture, entire files are deleted instead of time-range based deletion
        return true; // Return true for compatibility
    }
} 