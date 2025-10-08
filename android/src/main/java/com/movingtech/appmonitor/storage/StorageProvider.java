package com.movingtech.appmonitor.storage;

import java.util.List;
import java.util.Map;

/**
 * Interface for storage providers that handle different types of data storage
 * Supports both structured data (metrics, events) and unstructured data (logs)
 */
public interface StorageProvider {

    /**
     * Store a single metric data point
     * @param timestamp The timestamp of the metric
     * @param metricName Name/type of the metric
     * @param value The metric value
     * @param metadata Additional metadata for the metric
     * @return true if stored successfully, false otherwise
     */
    boolean storeMetric(long timestamp, String metricName, Object value, Map<String, Object> metadata, String sessionId,
                        String userId);

    /**
     * Store an event
     * @param timestamp The timestamp of the event
     * @param eventType Type of the event
     * @param eventData The event data
     * @return true if stored successfully, false otherwise
     */
    boolean storeEvent(long timestamp, String eventType, String eventName, Map<String, Object> eventData, String sessionId,
                       String userId);

    /**
     * Store log data
     * @param timestamp The timestamp of the log
     * @param logLevel Log level (DEBUG, INFO, WARN, ERROR)
     * @param message Log message
     * @param tag Optional tag for categorization
     * @return true if stored successfully, false otherwise
     */
    boolean storeLog(long timestamp, String logLevel, String message, String tag);

    /**
     * Retrieve metrics within a time range
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @param metricName Optional metric name filter
     * @return List of metrics
     */
    List<Map<String, Object>> getMetrics(long startTime, long endTime, String metricName);

    /**
     * Retrieve events within a time range
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @param eventType Optional event type filter
     * @return List of events
     */
    List<Map<String, Object>> getEvents(long startTime, long endTime, String eventType);

    /**
     * Retrieve logs within a time range
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @param logLevel Optional log level filter
     * @return List of logs
     */
    List<String> getLogs(long startTime, long endTime, String logLevel);

    /**
     * Initialize the storage provider
     * @return true if initialization was successful
     */
    boolean initialize();

    /**
     * Clean up resources and close connections
     */
    void cleanup();

    /**
     * Clear old data based on retention policy
     * @param retentionDays Number of days to retain data
     * @return Number of records cleaned up
     */
    int cleanupOldData(int retentionDays);
} 