# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-01-XX

### Added
- Initial release as a standalone React Native library
- Migrated from Git submodule to proper npm package
- TypeScript wrapper with full type definitions
- React Native TurboModule support
- Comprehensive documentation

### Features
- **Metrics Collection**: Performance metrics and custom metrics tracking
- **Event Tracking**: User interactions and application events
- **Logging System**: Structured logging with levels, tags, and metadata
- **Session Management**: Session ID generation and tracking
- **User ID Management**: User identification and replacement
- **API Latency Monitoring**: Automatic API call latency tracking
- **Data Synchronization**: Automatic transmission to backend APIs
- **Remote Configuration**: API-driven configuration management
- **Local Storage**: SQLite for structured data, file-based for logs
- **Thread Safety**: Non-blocking operations and overflow protection
- **System Metrics**: Automatic memory, battery, and temperature monitoring

### Native Modules
- `AppMonitor.java` - Core monitoring singleton
- `MetricsAggregator.java` - Metrics collection and processing
- `EventsAggregator.java` - Event tracking
- `LogsAggregator.java` - Log aggregation
- `ApiLatenciesAggregator.java` - API latency tracking
- `DataFlusher.java` - Backend synchronization
- `MetricsFetcher.java` - Automatic system metrics collection
- `ConfigManager.java` - Remote configuration management
- `StorageManager.java` - Data persistence
- `ApiClient.java` - HTTP client with latency tracking
- `AppMonitorBridge.kt` - React Native bridge

### JavaScript API
- `AppMonitor.addMetric(name, value)` - Add a metric
- `AppMonitor.addEvent(type, name, payload)` - Track an event
- `AppMonitor.addLog(level, message, tag, labels)` - Add a log entry
- `AppMonitor.getSessionId()` - Get current session ID
- `AppMonitor.replaceUserId(userId)` - Replace user ID
- `AppMonitor.resetUserId()` - Reset user ID to random UUID
- `AppMonitor.generateNewSession()` - Generate new session

### Documentation
- Comprehensive README with usage examples
- API latency tracking documentation
- Existing data recovery guide
- New logs architecture examples
- Migration guide from submodule
- Sample configuration file

### Build Configuration
- Gradle build configuration for Android
- React Native Builder Bob for TypeScript compilation
- ProGuard rules for release builds
- Consumer ProGuard rules

### Dependencies
- React Native 0.79.5+
- Kotlin 1.9.0
- OkHttp 4.12.0
- AndroidX libraries

[1.0.0]: https://github.com/nammayatri/react-native-app-monitor/releases/tag/v1.0.0
