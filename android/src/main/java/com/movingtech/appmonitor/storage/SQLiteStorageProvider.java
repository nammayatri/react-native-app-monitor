package com.movingtech.appmonitor.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite implementation of StorageProvider for structured data (metrics and events)
 */
public class SQLiteStorageProvider extends SQLiteOpenHelper implements StorageProvider {

    private static final String TAG = "SQLiteStorageProvider";
    private static final String DATABASE_NAME = "app_monitor.db";
    private static final int DATABASE_VERSION = 1;

    // Table names
    private static final String TABLE_METRICS = "metrics";
    private static final String TABLE_EVENTS = "events";
    private static final String TABLE_API_LATENCIES = "api_latencies";

    // Common columns
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_SESSION_ID = "session_id";

    // Metrics table columns
    private static final String COLUMN_METRIC_NAME = "metric_name";
    private static final String COLUMN_METRIC_VALUE = "metric_value";
    private static final String COLUMN_METRIC_METADATA = "metadata";
    // private static final String COLUMN_CPU_USAGE = "cpu_usage";
    // private static final String COLUMN_MEMORY_USAGE = "memory_usage";
    // private static final String COLUMN_TEMPERATURE = "temperature";
    // private static final String COLUMN_BATTERY_USAGE = "battery";

    // Events table columns
    private static final String COLUMN_EVENT_TYPE = "event_type";
    private static final String COLUMN_EVENT_NAME = "event_name";
    private static final String COLUMN_EVENT_DATA = "event_data";

    // API Latencies table columns
    private static final String COLUMN_ENDPOINT = "endpoint";
    private static final String COLUMN_METHOD = "method";
    private static final String COLUMN_LATENCY = "latency";
    private static final String COLUMN_STATUS_CODE = "statusCode";

//                "cpuUsage": 0.0,
//                "memoryUsage": 72.22870663084635,
//                "temperature": 25.0,
//                "battery": 100.0,
    // Create table statements
    private static final String CREATE_METRICS_TABLE = 
        "CREATE TABLE " + TABLE_METRICS + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
        COLUMN_METRIC_NAME + " TEXT NOT NULL, " +
                COLUMN_USER_ID + " TEXT NOT NULL, " +
                COLUMN_SESSION_ID + " TEXT NOT NULL, " +
        COLUMN_METRIC_VALUE + " TEXT NOT NULL, " +
        COLUMN_METRIC_METADATA + " TEXT" +
//                COLUMN_CPU_USAGE + "FLOAT," +
//                COLUMN_MEMORY_USAGE + "FLOAT," +
//                COLUMN_TEMPERATURE + "FLOAT," +
//                COLUMN_BATTERY_USAGE + "FLOAT" +

        ")";

    private static final String CREATE_EVENTS_TABLE = 
        "CREATE TABLE " + TABLE_EVENTS + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
        COLUMN_EVENT_TYPE + " TEXT NOT NULL, " +
                COLUMN_USER_ID + " TEXT NOT NULL, " +
                COLUMN_SESSION_ID + " TEXT NOT NULL, " +
        COLUMN_EVENT_DATA + " TEXT NOT NULL," +
                COLUMN_EVENT_NAME + " TEXT NOT NULL" +
        ")";

    private static final String CREATE_API_LATENCIES_TABLE = 
        "CREATE TABLE " + TABLE_API_LATENCIES + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_USER_ID + " TEXT NOT NULL, " +
                COLUMN_SESSION_ID + " TEXT NOT NULL, " +
        COLUMN_ENDPOINT + " TEXT NOT NULL, " +
        COLUMN_METHOD + " TEXT NOT NULL, " +
        COLUMN_LATENCY + " INTEGER NOT NULL, " +
        COLUMN_STATUS_CODE + " INTEGER NOT NULL, " +
        COLUMN_TIMESTAMP + " INTEGER NOT NULL" +
        ")";

    // Create index statements
    private static final String CREATE_METRICS_TIMESTAMP_INDEX = 
        "CREATE INDEX idx_metrics_timestamp ON " + TABLE_METRICS + " (" + COLUMN_TIMESTAMP + ")";
    
    private static final String CREATE_METRICS_NAME_INDEX = 
        "CREATE INDEX idx_metrics_name ON " + TABLE_METRICS + " (" + COLUMN_METRIC_NAME + ")";
    
    private static final String CREATE_EVENTS_TIMESTAMP_INDEX = 
        "CREATE INDEX idx_events_timestamp ON " + TABLE_EVENTS + " (" + COLUMN_TIMESTAMP + ")";
    
    private static final String CREATE_EVENTS_TYPE_INDEX = 
        "CREATE INDEX idx_events_type ON " + TABLE_EVENTS + " (" + COLUMN_EVENT_TYPE + ")";

    private static final String CREATE_API_LATENCIES_TIMESTAMP_INDEX = 
        "CREATE INDEX idx_api_latencies_timestamp ON " + TABLE_API_LATENCIES + " (" + COLUMN_TIMESTAMP + ")";
    
    private static final String CREATE_API_LATENCIES_ENDPOINT_INDEX = 
        "CREATE INDEX idx_api_latencies_endpoint ON " + TABLE_API_LATENCIES + " (" + COLUMN_ENDPOINT + ")";

    public SQLiteStorageProvider(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public SQLiteStorageProvider(Context context, String databaseName, int databaseVersion) {
        super(context, databaseName, null, databaseVersion);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_METRICS_TABLE);
        db.execSQL(CREATE_EVENTS_TABLE);
        db.execSQL(CREATE_API_LATENCIES_TABLE);
        db.execSQL(CREATE_METRICS_TIMESTAMP_INDEX);
        db.execSQL(CREATE_METRICS_NAME_INDEX);
        db.execSQL(CREATE_EVENTS_TIMESTAMP_INDEX);
        db.execSQL(CREATE_EVENTS_TYPE_INDEX);
        db.execSQL(CREATE_API_LATENCIES_TIMESTAMP_INDEX);
        db.execSQL(CREATE_API_LATENCIES_ENDPOINT_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_METRICS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EVENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_API_LATENCIES);
        db.execSQL("DROP INDEX IF EXISTS idx_metrics_timestamp");
        db.execSQL("DROP INDEX IF EXISTS idx_metrics_name");
        db.execSQL("DROP INDEX IF EXISTS idx_events_timestamp");
        db.execSQL("DROP INDEX IF EXISTS idx_events_type");
        db.execSQL("DROP INDEX IF EXISTS idx_api_latencies_timestamp");
        db.execSQL("DROP INDEX IF EXISTS idx_api_latencies_endpoint");
        onCreate(db);
    }

    @Override
    public boolean storeMetric(long timestamp, String metricName, Object value, Map<String, Object> metadata, String sessionId,
                               String userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_TIMESTAMP, timestamp);
            values.put(COLUMN_METRIC_NAME, metricName);
            values.put(COLUMN_METRIC_VALUE, String.valueOf(value));
            values.put(COLUMN_SESSION_ID, sessionId);
            values.put(COLUMN_USER_ID, userId);
            if (metadata != null && !metadata.isEmpty()) {
                JSONObject metadataJson = new JSONObject(metadata);
                values.put(COLUMN_METRIC_METADATA, metadataJson.toString());
            }

            long result = db.insert(TABLE_METRICS, null, values);
            return result != -1;
        } catch (Exception e) {
            Log.e(TAG, "Error storing metric", e);
            return false;
        }
    }

    @Override
    public boolean storeEvent(long timestamp, String eventType, String eventName, Map<String, Object> eventData, String sessionId,
                              String userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_TIMESTAMP, timestamp);
            values.put(COLUMN_EVENT_TYPE, eventType);
            values.put(COLUMN_EVENT_NAME, eventName);
            values.put(COLUMN_SESSION_ID, sessionId);
            values.put(COLUMN_USER_ID, userId);
            
            JSONObject eventDataJson = new JSONObject(eventData);
            values.put(COLUMN_EVENT_DATA, eventDataJson.toString());

            long result = db.insert(TABLE_EVENTS, null, values);
            return result != -1;
        } catch (Exception e) {
            Log.e(TAG, "Error storing event", e);
            return false;
        }
    }

    /**
     * Store API latency data
     */
    public boolean storeApiLatency(long timestamp, String endpoint, String method, long latency, int statusCode, String sessionId,
                                   String userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_TIMESTAMP, timestamp);
            values.put(COLUMN_ENDPOINT, endpoint);
            values.put(COLUMN_METHOD, method);
            values.put(COLUMN_LATENCY, latency);
            values.put(COLUMN_STATUS_CODE, statusCode);
            values.put(COLUMN_SESSION_ID, sessionId);
            values.put(COLUMN_USER_ID, userId);

            long result = db.insert(TABLE_API_LATENCIES, null, values);
            return result != -1;
        } catch (Exception e) {
            Log.e(TAG, "Error storing API latency", e);
            return false;
        }
    }

    @Override
    public boolean storeLog(long timestamp, String logLevel, String message, String tag) {
        // SQLite provider doesn't handle logs - delegated to file-based provider
        Log.w(TAG, "storeLog called on SQLite provider - logs should be handled by file provider");
        return false;
    }

    @Override
    public List<Map<String, Object>> getMetrics(long startTime, long endTime, String metricName) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = COLUMN_TIMESTAMP + " BETWEEN ? AND ?";
        String[] selectionArgs;

        if (metricName != null && !metricName.isEmpty()) {
            selection += " AND " + COLUMN_METRIC_NAME + " = ?";
            selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime), metricName};
        } else {
            selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime)};
        }

        try (Cursor cursor = db.query(TABLE_METRICS, null, selection, selectionArgs, 
                                     null, null, COLUMN_TIMESTAMP + " ASC")) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> metric = new HashMap<>();
                metric.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                metric.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                metric.put("metricName", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_NAME)));
                metric.put("value", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_VALUE)));
                metric.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                metric.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                String metadataStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_METADATA));
                if (metadataStr != null) {
                    try {
                        JSONObject metadataJson = new JSONObject(metadataStr);
                        Map<String, Object> metadataMap = new HashMap<>();
                        metadataJson.keys().forEachRemaining(key -> {
                            try {
                                metadataMap.put(key, metadataJson.get(key));
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing metadata", e);
                            }
                        });
                        metric.put("metadata", metadataMap);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing metadata JSON", e);
                    }
                }
                
                metrics.add(metric);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving metrics", e);
        }

        return metrics;
    }

    @Override
    public List<Map<String, Object>> getEvents(long startTime, long endTime, String eventType) {
        List<Map<String, Object>> events = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = COLUMN_TIMESTAMP + " BETWEEN ? AND ?";
        String[] selectionArgs;

        if (eventType != null && !eventType.isEmpty()) {
            selection += " AND " + COLUMN_EVENT_TYPE + " = ?";
            selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime), eventType};
        } else {
            selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime)};
        }

        try (Cursor cursor = db.query(TABLE_EVENTS, null, selection, selectionArgs, 
                                     null, null, COLUMN_TIMESTAMP + " ASC")) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> event = new HashMap<>();
                event.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                event.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                event.put("eventType", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_TYPE)));
                event.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                event.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                String eventDataStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_DATA));
                try {
                    JSONObject eventDataJson = new JSONObject(eventDataStr);
                    Map<String, Object> eventDataMap = new HashMap<>();
                    eventDataJson.keys().forEachRemaining(key -> {
                        try {
                            eventDataMap.put(key, eventDataJson.get(key));
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing event data", e);
                        }
                    });
                    event.put("eventData", eventDataMap);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing event data JSON", e);
                }
                
                events.add(event);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving events", e);
        }

        return events;
    }

    @Override
    public List<String> getLogs(long startTime, long endTime, String logLevel) {
        // SQLite provider doesn't handle logs - delegated to file-based provider
        Log.w(TAG, "getLogs called on SQLite provider - logs should be handled by file provider");
        return new ArrayList<>();
    }

    @Override
    public boolean initialize() {
        try {
            // Test database connection
            SQLiteDatabase db = getReadableDatabase();
            return db != null;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SQLite storage", e);
            return false;
        }
    }

    @Override
    public void cleanup() {
        close();
    }

    @Override
    public int cleanupOldData(int retentionDays) {
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
        SQLiteDatabase db = getWritableDatabase();
        
        int deletedMetrics = db.delete(TABLE_METRICS, COLUMN_TIMESTAMP + " < ?", 
                                      new String[]{String.valueOf(cutoffTime)});
        int deletedEvents = db.delete(TABLE_EVENTS, COLUMN_TIMESTAMP + " < ?", 
                                     new String[]{String.valueOf(cutoffTime)});
        int deletedApiLatencies = db.delete(TABLE_API_LATENCIES, COLUMN_TIMESTAMP + " < ?", 
                                           new String[]{String.valueOf(cutoffTime)});
        
        Log.i(TAG, "Cleaned up " + deletedMetrics + " metrics, " + deletedEvents + " events, and " + 
              deletedApiLatencies + " API latencies");
        return deletedMetrics + deletedEvents + deletedApiLatencies;
    }

    // Additional methods for DataFlusher
    
    /**
     * Get metrics with limit for batch processing
     */
    public List<Map<String, Object>> getMetrics(long startTime, long endTime, String metricName, int limit) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = COLUMN_TIMESTAMP + " BETWEEN ? AND ?";
        String[] selectionArgs;

        if (metricName != null && !metricName.isEmpty()) {
            selection += " AND " + COLUMN_METRIC_NAME + " = ?";
            selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime), metricName};
        } else {
            selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime)};
        }

        String limitStr = limit > 0 ? String.valueOf(limit) : null;

        try (Cursor cursor = db.query(TABLE_METRICS, null, selection, selectionArgs, 
                                     null, null, COLUMN_TIMESTAMP + " ASC", limitStr)) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> metric = new HashMap<>();
                metric.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                metric.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                metric.put("name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_NAME)));
                metric.put("value", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_VALUE)));
                metric.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                metric.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                String metadataStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_METADATA));
                if (metadataStr != null) {
                    try {
                        JSONObject metadataJson = new JSONObject(metadataStr);
                        Map<String, Object> metadataMap = new HashMap<>();
                        metadataJson.keys().forEachRemaining(key -> {
                            try {
                                metadataMap.put(key, metadataJson.get(key));
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing metadata", e);
                            }
                        });
                        metric.put("metadata", metadataMap);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing metadata JSON", e);
                    }
                }
                
                metrics.add(metric);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving metrics with limit", e);
        }

        return metrics;
    }
    
    /**
     * Get events with limit for batch processing
     */
    public List<Map<String, Object>> getEvents(long startTime, long endTime, String eventType, int limit) {
        List<Map<String, Object>> events = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = COLUMN_TIMESTAMP + " BETWEEN ? AND ?";
        String[] selectionArgs;

        if (eventType != null && !eventType.isEmpty()) {
            selection += " AND " + COLUMN_EVENT_TYPE + " = ?";
            selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime), eventType};
        } else {
            selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime)};
        }

        String limitStr = limit > 0 ? String.valueOf(limit) : null;

        try (Cursor cursor = db.query(TABLE_EVENTS, null, selection, selectionArgs, 
                                     null, null, COLUMN_TIMESTAMP + " ASC", limitStr)) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> event = new HashMap<>();
                event.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                event.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                event.put("type", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_TYPE)));
                event.put("event_name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_NAME)));
                event.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                event.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                String eventDataStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_DATA));
                try {
                    JSONObject eventDataJson = new JSONObject(eventDataStr);
                    Map<String, Object> eventDataMap = new HashMap<>();
                    eventDataJson.keys().forEachRemaining(key -> {
                        try {
                            eventDataMap.put(key, eventDataJson.get(key));
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing event data", e);
                        }
                    });
                    event.put("data", eventDataMap);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing event data JSON", e);
                }
                
                events.add(event);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving events with limit", e);
        }

        return events;
    }
    
    /**
     * Delete metrics in time range
     */
    public boolean deleteMetrics(long startTime, long endTime) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int deletedCount = db.delete(TABLE_METRICS, 
                COLUMN_TIMESTAMP + " BETWEEN ? AND ?", 
                new String[]{String.valueOf(startTime), String.valueOf(endTime)});
            
            Log.i(TAG, "Deleted " + deletedCount + " metrics from " + startTime + " to " + endTime);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting metrics", e);
            return false;
        }
    }
    
    /**
     * Delete events in time range
     */
    public boolean deleteEvents(long startTime, long endTime) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int deletedCount = db.delete(TABLE_EVENTS, 
                COLUMN_TIMESTAMP + " BETWEEN ? AND ?", 
                new String[]{String.valueOf(startTime), String.valueOf(endTime)});
            
            Log.i(TAG, "Deleted " + deletedCount + " events from " + startTime + " to " + endTime);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting events", e);
            return false;
        }
    }

    /**
     * Get metrics based only on batch size (latest records)
     */
    public List<Map<String, Object>> getMetrics(int limit) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String limitStr = limit > 0 ? String.valueOf(limit) : null;

        try (Cursor cursor = db.query(TABLE_METRICS, null, null, null, 
                                     null, null, COLUMN_TIMESTAMP + " ASC", limitStr)) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> metric = new HashMap<>();
                metric.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                metric.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                metric.put("name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_NAME)));
                metric.put("value", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_VALUE)));
                metric.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                metric.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                String metadataStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_METADATA));
                if (metadataStr != null) {
                    try {
                        JSONObject metadataJson = new JSONObject(metadataStr);
                        Map<String, Object> metadataMap = new HashMap<>();
                        metadataJson.keys().forEachRemaining(key -> {
                            try {
                                metadataMap.put(key, metadataJson.get(key));
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing metadata", e);
                            }
                        });
                        metric.put("metadata", metadataMap);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing metadata JSON", e);
                    }
                }
                
                metrics.add(metric);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving metrics with batch limit", e);
        }

        return metrics;
    }
    
    /**
     * Delete specific metrics by their IDs
     */
    public boolean deleteMetrics(List<Map<String, Object>> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            Log.d(TAG, "No metrics to delete");
            return true;
        }
        
        SQLiteDatabase db = getWritableDatabase();
        try {
            // Build IN clause for metric IDs
            StringBuilder whereClause = new StringBuilder(COLUMN_ID + " IN (");
            String[] whereArgs = new String[metrics.size()];
            
            for (int i = 0; i < metrics.size(); i++) {
                Map<String, Object> metric = metrics.get(i);
                Long id = (Long) metric.get("id");
                if (id != null) {
                    whereArgs[i] = String.valueOf(id);
                    if (i > 0) {
                        whereClause.append(",");
                    }
                    whereClause.append("?");
                }
            }
            whereClause.append(")");
            
            int deletedCount = db.delete(TABLE_METRICS, whereClause.toString(), whereArgs);
            
            Log.i(TAG, "Deleted " + deletedCount + " specific metrics by ID");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting specific metrics", e);
            return false;
        }
    }
    
    /**
     * Get events based only on batch size (latest records)
     */
    public List<Map<String, Object>> getEvents(int limit) {
        List<Map<String, Object>> events = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String limitStr = limit > 0 ? String.valueOf(limit) : null;

        try (Cursor cursor = db.query(TABLE_EVENTS, null, null, null, 
                                     null, null, COLUMN_TIMESTAMP + " ASC", limitStr)) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> event = new HashMap<>();
                event.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                event.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                event.put("type", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_TYPE)));
                event.put("event_name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_NAME)));
                event.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                event.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                String eventDataStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_DATA));
                try {
                    JSONObject eventDataJson = new JSONObject(eventDataStr);
                    Map<String, Object> eventDataMap = new HashMap<>();
                    eventDataJson.keys().forEachRemaining(key -> {
                        try {
                            eventDataMap.put(key, eventDataJson.get(key));
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing event data", e);
                        }
                    });
                    event.put("data", eventDataMap);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing event data JSON", e);
                }
                
                events.add(event);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving events with batch limit", e);
        }

        return events;
    }
    
    /**
     * Delete specific events by their IDs
     */
    public boolean deleteEvents(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            Log.d(TAG, "No events to delete");
            return true;
        }
        
        SQLiteDatabase db = getWritableDatabase();
        try {
            // Build IN clause for event IDs
            StringBuilder whereClause = new StringBuilder(COLUMN_ID + " IN (");
            String[] whereArgs = new String[events.size()];
            
            for (int i = 0; i < events.size(); i++) {
                Map<String, Object> event = events.get(i);
                Long id = (Long) event.get("id");
                if (id != null) {
                    whereArgs[i] = String.valueOf(id);
                    if (i > 0) {
                        whereClause.append(",");
                    }
                    whereClause.append("?");
                }
            }
            whereClause.append(")");
            
            int deletedCount = db.delete(TABLE_EVENTS, whereClause.toString(), whereArgs);
            
            Log.i(TAG, "Deleted " + deletedCount + " specific events by ID");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting specific events", e);
            return false;
        }
    }

    /**
     * Get API latencies with limit for batch processing
     */
    public List<Map<String, Object>> getApiLatencies(long startTime, long endTime, String endpoint, int limit) {
        List<Map<String, Object>> apiLatencies = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = COLUMN_TIMESTAMP + " BETWEEN ? AND ?";
        String[] selectionArgs;

        if (endpoint != null && !endpoint.isEmpty()) {
            selection += " AND " + COLUMN_ENDPOINT + " = ?";
            selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime), endpoint};
        } else {
            selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime)};
        }

        String limitStr = limit > 0 ? String.valueOf(limit) : null;

        try (Cursor cursor = db.query(TABLE_API_LATENCIES, null, selection, selectionArgs, 
                                     null, null, COLUMN_TIMESTAMP + " ASC", limitStr)) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> apiLatency = new HashMap<>();
                apiLatency.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                apiLatency.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                apiLatency.put("endpoint", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ENDPOINT)));
                apiLatency.put("method", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METHOD)));
                apiLatency.put("latency", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LATENCY)));
                apiLatency.put("statusCode", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS_CODE)));
                apiLatency.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                apiLatency.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                apiLatencies.add(apiLatency);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving API latencies with limit", e);
        }

        return apiLatencies;
    }

    /**
     * Delete API latencies in time range
     */
    public boolean deleteApiLatencies(long startTime, long endTime) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int deletedCount = db.delete(TABLE_API_LATENCIES, 
                COLUMN_TIMESTAMP + " BETWEEN ? AND ?", 
                new String[]{String.valueOf(startTime), String.valueOf(endTime)});
            
            Log.i(TAG, "Deleted " + deletedCount + " API latencies from " + startTime + " to " + endTime);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting API latencies", e);
            return false;
        }
    }

    /**
     * Get API latencies with limit
     */
    public List<Map<String, Object>> getApiLatencies(int limit) {
        List<Map<String, Object>> apiLatencies = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        
        String limitStr = limit > 0 ? String.valueOf(limit) : null;
        
        try (Cursor cursor = db.query(TABLE_API_LATENCIES, null, null, null, 
                                     null, null, COLUMN_TIMESTAMP + " ASC", limitStr)) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> apiLatency = new HashMap<>();
                apiLatency.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                apiLatency.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                apiLatency.put("endpoint", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ENDPOINT)));
                apiLatency.put("method", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METHOD)));
                apiLatency.put("latency", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LATENCY)));
                apiLatency.put("statusCode", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS_CODE)));
                apiLatency.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                apiLatency.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                apiLatencies.add(apiLatency);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving API latencies", e);
        }
        
        return apiLatencies;
    }

    /**
     * Delete specific API latencies by list
     */
    public boolean deleteApiLatencies(List<Map<String, Object>> apiLatencies) {
        if (apiLatencies.isEmpty()) {
            return true;
        }
        
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.beginTransaction();
            
            for (Map<String, Object> latency : apiLatencies) {
                Object idObj = latency.get("id");
                if (idObj != null) {
                    long id = (Long) idObj;
                    db.delete(TABLE_API_LATENCIES, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
                }
            }
            
            db.setTransactionSuccessful();
            Log.i(TAG, "Deleted " + apiLatencies.size() + " specific API latencies");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting specific API latencies", e);
            return false;
        } finally {
            db.endTransaction();
        }
    }
    
    // Methods to get maximum IDs for recovery process
    
    /**
     * Get the maximum ID from metrics table
     * Used during startup recovery to determine the last existing row
     */
    public long getMaxMetricsId() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT MAX(" + COLUMN_ID + ") FROM " + TABLE_METRICS, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting max metrics ID", e);
        }
        return 0; // Return 0 if no records or error
    }
    
    /**
     * Get the maximum ID from events table
     * Used during startup recovery to determine the last existing row
     */
    public long getMaxEventsId() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT MAX(" + COLUMN_ID + ") FROM " + TABLE_EVENTS, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting max events ID", e);
        }
        return 0; // Return 0 if no records or error
    }
    
    /**
     * Get the maximum ID from api_latencies table
     * Used during startup recovery to determine the last existing row
     */
    public long getMaxApiLatenciesId() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT MAX(" + COLUMN_ID + ") FROM " + TABLE_API_LATENCIES, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting max API latencies ID", e);
        }
        return 0; // Return 0 if no records or error
    }
    
    /**
     * Get metrics with ID limit for recovery processing
     * Only fetches records with ID <= maxId to avoid processing new data during recovery
     */
    public List<Map<String, Object>> getMetricsUpToId(int limit, long maxId) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = COLUMN_ID + " <= ?";
        String[] selectionArgs = {String.valueOf(maxId)};
        String limitStr = limit > 0 ? String.valueOf(limit) : null;

        try (Cursor cursor = db.query(TABLE_METRICS, null, selection, selectionArgs, 
                                     null, null, COLUMN_TIMESTAMP + " ASC", limitStr)) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> metric = new HashMap<>();
                metric.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                metric.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                metric.put("name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_NAME)));
                metric.put("value", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_VALUE)));
                metric.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                metric.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                String metadataStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METRIC_METADATA));
                if (metadataStr != null) {
                    try {
                        JSONObject metadataJson = new JSONObject(metadataStr);
                        Map<String, Object> metadataMap = new HashMap<>();
                        metadataJson.keys().forEachRemaining(key -> {
                            try {
                                metadataMap.put(key, metadataJson.get(key));
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing metadata", e);
                            }
                        });
                        metric.put("metadata", metadataMap);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing metadata JSON", e);
                    }
                }
                
                metrics.add(metric);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving metrics up to ID " + maxId, e);
        }

        return metrics;
    }
    
    /**
     * Get events with ID limit for recovery processing
     * Only fetches records with ID <= maxId to avoid processing new data during recovery
     */
    public List<Map<String, Object>> getEventsUpToId(int limit, long maxId) {
        List<Map<String, Object>> events = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = COLUMN_ID + " <= ?";
        String[] selectionArgs = {String.valueOf(maxId)};
        String limitStr = limit > 0 ? String.valueOf(limit) : null;

        try (Cursor cursor = db.query(TABLE_EVENTS, null, selection, selectionArgs, 
                                     null, null, COLUMN_TIMESTAMP + " ASC", limitStr)) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> event = new HashMap<>();
                event.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                event.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                event.put("type", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_TYPE)));
                event.put("event_name", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_NAME)));
                event.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                event.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                String eventDataStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EVENT_DATA));
                try {
                    JSONObject eventDataJson = new JSONObject(eventDataStr);
                    Map<String, Object> eventDataMap = new HashMap<>();
                    eventDataJson.keys().forEachRemaining(key -> {
                        try {
                            eventDataMap.put(key, eventDataJson.get(key));
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing event data", e);
                        }
                    });
                    event.put("data", eventDataMap);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing event data JSON", e);
                }
                
                events.add(event);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving events up to ID " + maxId, e);
        }

        return events;
    }
    
    /**
     * Get API latencies with ID limit for recovery processing
     * Only fetches records with ID <= maxId to avoid processing new data during recovery
     */
    public List<Map<String, Object>> getApiLatenciesUpToId(int limit, long maxId) {
        List<Map<String, Object>> apiLatencies = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        
        String selection = COLUMN_ID + " <= ?";
        String[] selectionArgs = {String.valueOf(maxId)};
        String limitStr = limit > 0 ? String.valueOf(limit) : null;
        
        try (Cursor cursor = db.query(TABLE_API_LATENCIES, null, selection, selectionArgs, 
                                     null, null, COLUMN_TIMESTAMP + " ASC", limitStr)) {
            
            while (cursor.moveToNext()) {
                Map<String, Object> apiLatency = new HashMap<>();
                apiLatency.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                apiLatency.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                apiLatency.put("endpoint", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ENDPOINT)));
                apiLatency.put("method", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_METHOD)));
                apiLatency.put("latency", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LATENCY)));
                apiLatency.put("statusCode", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS_CODE)));
                apiLatency.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
                apiLatency.put("user_id", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                
                apiLatencies.add(apiLatency);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving API latencies up to ID " + maxId, e);
        }
        
        return apiLatencies;
    }

    /**
     * Get database name (for StorageManager)
     */
    public String getDatabaseName() {
        return DATABASE_NAME;
    }
} 