package com.movingtech.appmonitor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralized default configuration values for all app monitor components
 */
public final class ConfigDefaults {
    
    // Private constructor to prevent instantiation
    private ConfigDefaults() {}
    
    // Common defaults
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final long DEFAULT_PROCESS_INTERVAL_MS = 5000;
    public static final int DEFAULT_MAX_QUEUE_SIZE = 10000;
    public static final boolean DEFAULT_ENABLE_STATS = false;
    
    // Metrics defaults
    public static final boolean DEFAULT_ENABLE_AGGREGATION = false;
    
    // Logs defaults
    public static final int DEFAULT_MAX_LOGS_PER_FILE = 1000;
    public static final Set<String> DEFAULT_WHITELISTED_LOG_LEVELS = new HashSet<>(
        Arrays.asList("error", "warn", "debug", "info"));
    
    // Storage defaults
    public static final String DEFAULT_DATABASE_NAME = "app_monitor.db";
    public static final int DEFAULT_DATABASE_VERSION = 1;
    public static final long DEFAULT_RETENTION_DAYS = 7;
    public static final boolean DEFAULT_ENABLE_SQLITE = true;
    public static final boolean DEFAULT_ENABLE_FILE_STORAGE = true;
    
    // App defaults
    public static final boolean DEFAULT_ENABLE_DEBUG_LOGGING = false;
    public static final long DEFAULT_SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    public static final String DEFAULT_ENVIRONMENT = "production";
    
    // Data Flusher defaults
    public static final long DEFAULT_FLUSH_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes
    public static final long DEFAULT_FLUSH_INITIAL_DELAY_MS = 30 * 1000; // 30 seconds
    public static final boolean DEFAULT_ENABLE_METRICS_FLUSH = true;
    public static final boolean DEFAULT_ENABLE_EVENTS_FLUSH = true;
    public static final boolean DEFAULT_ENABLE_LOGS_FLUSH = true;
    public static final boolean DEFAULT_FLUSH_ON_SHUTDOWN = true;
    public static final boolean DEFAULT_FLUSH_EXISTING_DATA_ON_STARTUP = true;
    
    // Metrics Fetcher defaults
    public static final long DEFAULT_COLLECTION_INTERVAL_MS = 30 * 1000; // 30 seconds
    public static final long DEFAULT_FETCHER_INITIAL_DELAY_MS = 5 * 1000; // 5 seconds
    public static final boolean DEFAULT_ENABLE_MEMORY_METRICS = true;
    public static final boolean DEFAULT_ENABLE_BATTERY_METRICS = true;
    public static final boolean DEFAULT_ENABLE_TEMPERATURE_METRICS = true;
} 