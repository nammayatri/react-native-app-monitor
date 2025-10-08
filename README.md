# Android App Monitor SDK

A comprehensive monitoring solution for Android applications with real-time data collection, storage, and backend synchronization.

## 🚀 Features

- **📊 Metrics Collection**: Performance metrics, custom metrics, and latency tracking
- **📝 Event Tracking**: User interactions, application events, and custom events
- **🔍 Logging System**: Structured logging with levels, tags, and metadata
- **🔄 Data Synchronization**: Automatic transmission to backend APIs
- **⚙️ Remote Configuration**: API-driven configuration management
- **💾 Local Storage**: SQLite for structured data, file-based for logs
- **📈 Statistics**: Optional performance monitoring and analytics
- **🔒 Thread Safety**: Non-blocking operations and overflow protection
- **🤖 Automatic System Metrics**: Memory usage, battery level, and temperature monitoring

## 🏗️ Architecture

### Core Components

```
AppMonitor (Singleton)
├── ConfigManager          # Remote configuration management
├── StorageManager         # Coordinates storage providers
│   ├── SQLiteProvider    # Metrics & events storage
│   └── FileProvider      # Log file storage
├── MetricsAggregator     # Collects and processes metrics
├── EventsAggregator      # Handles event collection
├── LogsAggregator        # Manages log aggregation
├── MetricsFetcher        # Automatic system metrics collection
└── DataFlusher           # Backend synchronization
```

### Data Flow

```
App → Aggregators → Storage → DataFlusher → Backend API
      ↑
MetricsFetcher (System Metrics)
                     ↓
                Configuration ← ConfigManager ← Remote API
```

## 📦 Quick Start

### 1. Basic Initialization

```java
// Get the singleton instance
AppMonitor monitor = AppMonitor.getInstance(context);

// Initialize with API configuration
monitor.initialize("https://your-api.com", "your-api-key");
```

### 2. Collect Data

```java
// Add metrics
monitor.addMetric("cpu_usage", 75.5);
monitor.addMetric("memory_usage", 1024, Map.of("type", "heap"));

// Track events
monitor.addEvent("user_action", "button_click", 
    Map.of("button_id", "login", "screen", "auth"));

// Log messages
monitor.addLog("INFO", "User login successful", "AUTH");
monitor.addLog("ERROR", "Network timeout", "NETWORK", 
    Map.of("endpoint", "/api/login"));
```

### 3. Automatic System Metrics

The system automatically collects system metrics in the background:

```java
// These metrics are collected automatically every 30 seconds:
// - memory_usage (percentage)
// - memory_total, memory_available (bytes)
// - heap_usage (percentage) 
// - heap_allocated, heap_max (bytes)
// - battery_level (percentage)
// - battery_temperature (celsius)
// - cpu_temperature (celsius)

// Force immediate collection
monitor.getMetricsFetcher().collectNow();

// Check if fetcher is running
boolean isRunning = monitor.getMetricsFetcher().isRunning();
```

### 4. Automatic Synchronization

Data is automatically sent to your backend:
- **Metrics**: `POST /ingest/metrics` (batched)
- **Events**: `POST /ingest/events` (individual)
- **Logs**: `POST /ingest/logs` (individual)

## ⚙️ Configuration

### Remote Configuration

The system supports API-driven configuration updates:

```json
{
  "metrics": {
    "batchSize": 100,
    "processIntervalMs": 5000,
    "maxQueueSize": 10000,
    "enableStats": false,
    "enableAggregation": false
  },
  "events": {
    "batchSize": 100,
    "processIntervalMs": 5000,
    "maxQueueSize": 10000,
    "enableStats": false
  },
  "logs": {
    "batchSize": 100,
    "processIntervalMs": 5000,
    "maxQueueSize": 10000,
    "maxLogsPerFile": 1000,
    "enableStats": false
  },
  "dataFlusher": {
    "flushIntervalMs": 600000,
    "initialDelayMs": 30000,
    "batchSize": 100,
    "enableStats": false,
    "enableMetricsFlush": true,
    "enableEventsFlush": true,
    "enableLogsFlush": true,
    "flushOnShutdown": true
  },
  "metricsFetcher": {
    "collectionIntervalMs": 30000,
    "initialDelayMs": 5000,
    "enableMemoryMetrics": true,
    "enableBatteryMetrics": true,
    "enableTemperatureMetrics": true,
    "enableStats": false
  },
  "storage": {
    "databaseName": "app_monitor.db",
    "databaseVersion": 1,
    "retentionDays": 7,
    "enableSQLite": true,
    "enableFileStorage": true
  },
  "app": {
    "enableDebugLogging": false,
    "sessionTimeoutMs": 1800000,
    "userId": "unknown",
    "environment": "production"
  }
}
```

### Local Configuration

```java
// Initialize with custom refresh interval
monitor.initialize("https://api.company.com", "api-key", 15 * 60 * 1000);

// Set user context
monitor.setUserId("user_12345");

// Force configuration refresh
monitor.refreshConfiguration();
```

## 📡 API Integration

### Backend Endpoints

Your backend should implement these endpoints:

#### Metrics Endpoint
```
POST /ingest/metrics
Content-Type: application/json
Authorization: Bearer {api-key}

{
  "sessionId": "session_123",
  "userId": "user123",
  "os": "Android 12",
  "metrics": [...],
  "latencies": [...]
}
```

#### Events Endpoint
```
POST /ingest/events
Content-Type: application/json
Authorization: Bearer {api-key}

{
  "sessionId": "session_123",
  "userId": "user123",
  "timestamp": 1234567890000,
  "event_type": "user_action",
  "event_name": "button_click",
  "event_payload": {...},
  "metadata": {...},
  "os": "Android 12"
}
```

#### Logs Endpoint
```
POST /ingest/logs
Content-Type: application/json
Authorization: Bearer {api-key}

{
  "sessionId": "session_123",
  "userId": "user123",
  "message": "Log message",
  "level": "INFO",
  "os": "Android 12",
  "timestamp": 1234567890000,
  "labels": {...},
  "tag": "APP",
  "metadata": {...}
}
```

## 🔧 Advanced Usage

### Custom Session Management

```java
// Set custom session ID for all components
monitor.setSessionId("custom_session_id");

// Generate new session ID for all components
monitor.generateNewSession();

// Get current session ID
String currentSession = monitor.getSessionId();

// Update user context
monitor.setUserId("new_user_id");
```

### Manual Operations

```java
// Force immediate data flush
monitor.flush();

// Stop/start monitoring
monitor.stopMonitoring();
monitor.startMonitoring();

// Get system status
String status = monitor.getMonitoringStatus();
System.out.println(status);
```

### Statistics Monitoring

```java
// Enable statistics in configuration first
if (config.enableStats) {
    var metricsStats = monitor.getMetricsStats();
    var eventsStats = monitor.getEventsStats();
    var logsStats = monitor.getLogsStats();
    
    System.out.println(metricsStats.getFormattedStats());
}
```

### System Metrics Management

```java
// Get MetricsFetcher for advanced operations
MetricsFetcher fetcher = monitor.getMetricsFetcher();

// Force immediate collection of all system metrics
fetcher.collectNow();

// Check fetcher status
boolean isRunning = fetcher.isRunning();
MetricsFetcher.MetricsStats stats = fetcher.getStats();

// Get collected metrics statistics
System.out.println(stats.getFormattedStats());
```

## 🛡️ Error Handling

The system includes comprehensive error handling:

- **Network Failures**: Graceful degradation, data preserved locally
- **Storage Errors**: Fallback mechanisms and error logging
- **Configuration Errors**: Continue with cached/default configuration
- **Queue Overflow**: Temporary overflow queues prevent data loss
- **Thread Safety**: All operations are thread-safe and non-blocking

## 📱 Performance

### Memory Efficiency
- **Non-blocking Queues**: ConcurrentLinkedQueue for zero-contention
- **Configurable Limits**: Prevent memory bloat with queue size limits
- **Overflow Protection**: Temporary queues handle spikes
- **Automatic Cleanup**: Successful transmissions clean up storage

### Thread Management
- **Single Executors**: One thread per aggregator + one for data flushing
- **Non-daemon Threads**: Ensures data integrity during app lifecycle
- **Graceful Shutdown**: Proper cleanup and final data flush

### Network Optimization
- **Batched Requests**: Reduce HTTP overhead for metrics
- **Compression**: JSON payload optimization
- **Retry Logic**: Automatic retry with exponential backoff
- **Connection Pooling**: Efficient HTTP connection management

## 🔄 Lifecycle Management

```java
public class MyApplication extends Application {
    private AppMonitor monitor;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize monitoring
        monitor = AppMonitor.getInstance(this);
        monitor.initialize("https://api.company.com", "api-key");
    }
    
    @Override
    public void onTerminate() {
        // Graceful shutdown
        if (monitor != null) {
            monitor.shutdown();
        }
        super.onTerminate();
    }
}
```

## 📊 Monitoring Dashboard

The system provides comprehensive monitoring status:

```java
String status = monitor.getMonitoringStatus();
```

Example output:
```
=== App Monitor Status ===
Initialized: true
User ID: user_12345
Config Available: true
Last Config Update: Mon Jan 15 10:30:25 PST 2024

=== Current Configuration ===
Metrics - Batch: 100, Interval: 5000ms, Queue: 10000, Stats: true, Aggregation: false
Events - Batch: 100, Interval: 5000ms, Queue: 10000, Stats: true
Logs - Batch: 100, Interval: 5000ms, Queue: 10000, MaxPerFile: 1000, Stats: true

=== Statistics ===
Metrics: Received: 1250, Processed: 1240, Success Rate: 99.2%
Events: Received: 450, Processed: 450, Success Rate: 100%
Logs: Received: 890, Processed: 885, Success Rate: 99.4%
```

## 🎯 Best Practices

1. **Initialize Early**: Set up monitoring in Application.onCreate()
2. **Set User Context**: Update userId when user logs in/out
3. **Use Structured Logging**: Include meaningful tags and metadata
4. **Monitor Statistics**: Enable stats in development/staging
5. **Test Configuration**: Verify remote config updates work
6. **Handle Shutdown**: Call shutdown() in Application.onTerminate()
7. **Network Awareness**: System handles offline scenarios gracefully

## 📝 License

This project is part of the Namma Yatri open-source initiative.

---

For detailed examples and API reference, see [usage_example.md](usage_example.md). 