package com.movingtech.appmonitor;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MetricsFetcher actively collects system metrics and feeds them to MetricsAggregator
 * Collects cpuUsage, memoryUsage, temperature, and battery metrics periodically
 * Integrates with AppMonitor's centralized monitoring system
 */
public class MetricsFetcher implements ConfigManager.ConfigUpdateListener {

    private static final String TAG = "MetricsFetcher";
    
    // Metric names
    private static final String METRIC_CPU_USAGE = "cpuUsage";
    private static final String METRIC_MEMORY_USAGE = "memoryUsage";
    private static final String METRIC_TEMPERATURE = "temperature";
    private static final String METRIC_BATTERY = "battery";

    // Dependencies
    private final Context context;
    private final ConfigManager configManager;
    private MetricsAggregator metricsAggregator; // Will be injected by AppMonitor
    
    // Configuration
    private volatile ConfigManager.MetricsFetcherConfig config;
    
    // Executor and task management
    private final ScheduledExecutorService executorService;
    private ScheduledFuture<?> metricsCollectionTask;
    
    // System services
    private final ActivityManager activityManager;

    // State
    private boolean isRunning = false;
    
    // Statistics
    private long totalMetricsCollected = 0;
    private long lastCollectionTime = 0;

    public MetricsFetcher(Context context) {
        this.context = context.getApplicationContext();
        this.configManager = ConfigManager.getInstance(context);
        
        // Load initial configuration
        this.config = configManager.getMetricsFetcherConfig();
        
        // Initialize executor
        this.executorService = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "MetricsFetcher"));
        
        // Initialize system services
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        
        // Register for config updates
        configManager.setConfigUpdateListener(this);
        
        Log.i(TAG, "MetricsFetcher initialized");
    }

    /**
     * Set MetricsAggregator (called by AppMonitor)
     */
    public void setMetricsAggregator(MetricsAggregator metricsAggregator) {
        this.metricsAggregator = metricsAggregator;
        Log.i(TAG, "MetricsAggregator set for MetricsFetcher");
    }

    /**
     * Start periodic metrics collection
     */
    public void startMetricsFetcher() {
        if (isRunning) {
            Log.w(TAG, "MetricsFetcher already running");
            return;
        }
        
        if (metricsAggregator == null) {
            Log.e(TAG, "Cannot start MetricsFetcher - MetricsAggregator not set");
            return;
        }
        
        if (metricsCollectionTask != null && !metricsCollectionTask.isCancelled()) {
            metricsCollectionTask.cancel(false);
        }

        // Start the metrics collection task with configured interval
        metricsCollectionTask = executorService.scheduleWithFixedDelay(
            this::collectMetrics,
            config.initialDelayMs,
            config.collectionIntervalMs,
            TimeUnit.MILLISECONDS
        );
        
        isRunning = true;
        Log.i(TAG, "MetricsFetcher started with interval: " + config.collectionIntervalMs + "ms");
    }

    /**
     * Stop metrics collection
     */
    public void stopMetricsFetcher() {
        if (!isRunning) {
            Log.w(TAG, "MetricsFetcher not running");
            return;
        }
        
        if (metricsCollectionTask != null && !metricsCollectionTask.isCancelled()) {
            metricsCollectionTask.cancel(false);
            metricsCollectionTask = null;
        }
        
        isRunning = false;
        Log.i(TAG, "MetricsFetcher stopped");
    }

    /**
     * Collect all configured system metrics
     */
    private void collectMetrics() {
        try {
            long startTime = System.currentTimeMillis();
            long timestamp = System.currentTimeMillis();
            int metricsCollected = 0;
            
            // Collect CPU usage
            double cpuUsage = getCpuUsage();
            if (cpuUsage >= 0) {
                metricsAggregator.addMetric(METRIC_CPU_USAGE, cpuUsage, 
                    createMetadata("percentage", "system_cpu"), timestamp);
                metricsCollected++;
            }
            
            // Collect memory usage
            double memoryUsage = getMemoryUsage();
            if (memoryUsage >= 0) {
                metricsAggregator.addMetric(METRIC_MEMORY_USAGE, memoryUsage, 
                    createMetadata("percentage", "system_memory"), timestamp);
                metricsCollected++;
            }
            
            // Collect temperature
            double temperature = getTemperature();
            if (temperature >= 0) {
                metricsAggregator.addMetric(METRIC_TEMPERATURE, temperature, 
                    createMetadata("celsius", "system_thermal"), timestamp);
                metricsCollected++;
            }
            
            // Collect battery
            double battery = getBattery();
            if (battery >= 0) {
                metricsAggregator.addMetric(METRIC_BATTERY, battery, 
                    createMetadata("percentage", "system_battery"), timestamp);
                metricsCollected++;
            }
            
            long collectionTime = System.currentTimeMillis() - startTime;
            totalMetricsCollected += metricsCollected;
            lastCollectionTime = System.currentTimeMillis();
            
            Log.d(TAG, "Collected " + metricsCollected + " metrics in " + collectionTime + 
                  "ms (CPU: " + cpuUsage + "%, Memory: " + memoryUsage + "%, Temp: " + temperature + "°C, Battery: " + battery + "%)");
            
        } catch (Exception e) {
            Log.e(TAG, "Error collecting metrics", e);
        }
    }
    
    /**
     * Get system memory usage percentage
     */
    private double getMemoryUsage() {
        try {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            
            // Calculate memory usage percentage
            long totalMemory = memoryInfo.totalMem;
            long availableMemory = memoryInfo.availMem;
            long usedMemory = totalMemory - availableMemory;
            
            return (double) usedMemory / totalMemory * 100.0;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting memory usage", e);
            return -1;
        }
    }
    
    /**
     * Get CPU usage percentage by reading /proc/stat
     */
    private double getCpuUsage() {
        try {
            // Read /proc/stat for CPU usage
            BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            reader.close();
            
            if (line != null && line.startsWith("cpu ")) {
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 8) {
                    // Parse CPU time values
                    long user = Long.parseLong(tokens[1]);
                    long nice = Long.parseLong(tokens[2]);
                    long system = Long.parseLong(tokens[3]);
                    long idle = Long.parseLong(tokens[4]);
                    long iowait = Long.parseLong(tokens[5]);
                    long irq = Long.parseLong(tokens[6]);
                    long softirq = Long.parseLong(tokens[7]);
                    
                    long totalTime = user + nice + system + idle + iowait + irq + softirq;
                    long idleTime = idle + iowait;
                    long workTime = totalTime - idleTime;
                    
                    // Calculate CPU usage percentage
                    return (double) workTime / totalTime * 100.0;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not read CPU usage from /proc/stat: " + e.getMessage());
        }
        
        // Fallback: estimate CPU usage from process info (less accurate)
        try {
            return getEstimatedCpuUsage();
        } catch (Exception e) {
            Log.e(TAG, "Error getting CPU usage", e);
            return -1;
        }
    }
    
    /**
     * Estimate CPU usage from process information (fallback method)
     */
    private double getEstimatedCpuUsage() {
        try {
            // Get current process CPU time
            long startTime = System.currentTimeMillis();
            long startCpuTime = android.os.Process.getElapsedCpuTime();
            
            // Wait a short time
            Thread.sleep(100);
            
            long endTime = System.currentTimeMillis();
            long endCpuTime = android.os.Process.getElapsedCpuTime();
            
            // Calculate usage for this process (not system-wide)
            long cpuTimeDiff = endCpuTime - startCpuTime;
            long realTimeDiff = endTime - startTime;
            
            if (realTimeDiff > 0) {
                return (double) cpuTimeDiff / realTimeDiff * 100.0;
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not estimate CPU usage: " + e.getMessage());
        }
        
        return -1; // Unable to determine
    }
    
    /**
     * Get system temperature in Celsius
     */
    private double getTemperature() {
        // Try to read temperature from thermal zones first
        double temp = getTemperatureFromThermalZones();
        if (temp >= 0) {
            return temp;
        }
        
        // Try common temperature file paths
        temp = getTemperatureFromFiles();
        if (temp >= 0) {
            return temp;
        }
        
        return -1; // Unable to determine temperature
    }
    
    /**
     * Read temperature from thermal zone files
     */
    private double getTemperatureFromThermalZones() {
        try {
            // Check common thermal zone paths
            for (int i = 0; i < 10; i++) {
                File thermalFile = new File("/sys/class/thermal/thermal_zone" + i + "/temp");
                if (thermalFile.exists() && thermalFile.canRead()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(thermalFile))) {
                        String line = reader.readLine();
                        if (line != null && !line.trim().isEmpty()) {
                            try {
                                // Temperature is usually in millidegrees Celsius
                                long tempMilliC = Long.parseLong(line.trim());
                                double tempC = tempMilliC / 1000.0;
                                
                                if (tempC > 0 && tempC < 150) { // Sanity check
                                    Log.d(TAG, "Read temperature from thermal zone " + i + ": " + tempC + "°C");
                                    return tempC;
                                }
                            } catch (NumberFormatException e) {
                                // Skip invalid temperature readings
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not read thermal zone temperatures: " + e.getMessage());
        }
        
        return -1;
    }
    
    /**
     * Read temperature from common file paths
     */
    private double getTemperatureFromFiles() {
        // Common CPU temperature file paths
        String[] tempPaths = {
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp",
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/class/hwmon/hwmon1/temp1_input",
            "/proc/driver/tegra_soc/cputemp"
        };
        
        for (String path : tempPaths) {
            File tempFile = new File(path);
            if (tempFile.exists() && tempFile.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(tempFile))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        try {
                            double temp = Double.parseDouble(line.trim());
                            
                            // Different files may use different units
                            if (temp > 1000) {
                                temp = temp / 1000.0; // Convert from millidegrees
                            }
                            
                            if (temp > 0 && temp < 150) { // Sanity check
                                Log.d(TAG, "Read temperature from " + path + ": " + temp + "°C");
                                return temp;
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid temperature readings
                        }
                    }
                } catch (IOException e) {
                    // Try next path
                }
            }
        }
        
        return -1;
    }
    
    /**
     * Get battery percentage
     */
    private double getBattery() {
        try {
            // Get battery status using intent filter
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                
                if (level >= 0 && scale > 0) {
                    return (double) level * 100.0 / scale;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting battery level", e);
        }
        
        return -1; // Unable to determine battery level
    }
    
    /**
     * Create standard metadata for metrics
     */
    private Map<String, Object> createMetadata(String unit, String source) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("unit", unit);
        metadata.put("source", source);
        metadata.put("collector", "MetricsFetcher");
        return metadata;
    }
    
    /**
     * Get collection statistics
     */
    public MetricsStats getStats() {
        return new MetricsStats(
            totalMetricsCollected,
            lastCollectionTime,
            isRunning,
            config.collectionIntervalMs
        );
    }
    
    /**
     * Force immediate collection
     */
    public void collectNow() {
        if (metricsAggregator != null) {
            collectMetrics();
        } else {
            Log.w(TAG, "Cannot collect now - MetricsAggregator not set");
        }
    }
    
    /**
     * Check if fetcher is running
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Shutdown the fetcher
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down MetricsFetcher");
        
        stopMetricsFetcher();
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        Log.i(TAG, "MetricsFetcher shutdown complete");
    }
    
    // ConfigUpdateListener implementation
    
    @Override
    public void onConfigUpdated(org.json.JSONObject newConfig) {
        ConfigManager.MetricsFetcherConfig newFetcherConfig = configManager.getMetricsFetcherConfig();
        this.config = newFetcherConfig;
        
        // Restart collection with new interval if running
        if (isRunning) {
            stopMetricsFetcher();
            startMetricsFetcher();
        }
        
        Log.i(TAG, "MetricsFetcher configuration updated - interval: " + config.collectionIntervalMs + "ms");
    }
    
    @Override
    public void onConfigError(String error) {
        Log.e(TAG, "Configuration error: " + error);
        // Continue with current configuration
    }
    
    // Inner classes
    
    /**
     * Metrics collection statistics
     */
    public static class MetricsStats {
        public final long totalCollected;
        public final long lastCollectionTime;
        public final boolean isRunning;
        public final long intervalMs;
        
        public MetricsStats(long totalCollected, long lastCollectionTime, boolean isRunning, long intervalMs) {
            this.totalCollected = totalCollected;
            this.lastCollectionTime = lastCollectionTime;
            this.isRunning = isRunning;
            this.intervalMs = intervalMs;
        }
        
        public String getFormattedStats() {
            StringBuilder sb = new StringBuilder();
            sb.append("MetricsFetcher Stats:\n");
            sb.append("Running: ").append(isRunning).append("\n");
            sb.append("Total Collected: ").append(totalCollected).append("\n");
            sb.append("Collection Interval: ").append(intervalMs).append("ms\n");
            sb.append("Last Collection: ");
            if (lastCollectionTime > 0) {
                sb.append(new java.util.Date(lastCollectionTime));
            } else {
                sb.append("Never");
            }
            sb.append("\n");
            return sb.toString();
        }
    }
}
