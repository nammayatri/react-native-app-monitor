package com.movingtech.appmonitor;

import android.content.Context;
import android.util.Log;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp interceptor for capturing API latency data
 * Measures request/response time and forwards to ApiLatenciesAggregator
 */
public class ApiLatencyInterceptor implements Interceptor {

    private static final String TAG = "ApiLatencyInterceptor";

    private final boolean enabled;
    private final Context context;

    public ApiLatencyInterceptor(boolean enabled, Context context) {
        this.enabled = enabled;
        this.context = context;
    }

    public ApiLatencyInterceptor(Context context){
        this.enabled = true;
        this.context = context;
    }


    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        
        if (!enabled) {
            // If disabled or aggregator not available, just proceed with the request
            return chain.proceed(request);
        }

        long startTime = System.currentTimeMillis();
        Response response = null;
        boolean requestSuccessful = false;
        int statusCode = 0;

        try {
            response = chain.proceed(request);
            requestSuccessful = true;
            statusCode = response.code();
            return response;
        } catch (IOException e) {
            // Network error occurred
            requestSuccessful = false;
            statusCode = -1; // Use -1 to indicate network error
            throw e;
        } finally {
            try {
                long endTime = System.currentTimeMillis();
                long latency = endTime - startTime;

                // Extract endpoint and method
                String endpoint = extractEndpoint(request);
                String method = request.method();

                // Record the API latency
                boolean recorded = AppMonitor.getInstance(context).addApiLatency(endpoint, method, latency, statusCode, startTime);
                
                if (recorded) {
                    Log.d(TAG, String.format("Recorded API latency: %s %s - %dms (status: %d)", 
                           method, endpoint, latency, statusCode));
                } else {
                    Log.w(TAG, String.format("Failed to record API latency: %s %s - %dms", 
                           method, endpoint, latency));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error recording API latency", e);
                // Don't throw - we don't want to break the actual API call
            }
        }
    }

    /**
     * Extract endpoint from request URL
     * Removes query parameters and base URL to get clean endpoint
     */
    private String extractEndpoint(Request request) {
        try {
            String url = request.url().toString();
            String path = request.url().encodedPath();
            
            // If path is empty, use the full URL
            if (path == null || path.isEmpty() || path.equals("/")) {
                // For cases where there's no path, use the host
                return request.url().host();
            }
            
            // Remove query parameters if any exist
            String endpoint = path;
            if (request.url().query() != null) {
                // Keep the path, ignore query parameters
                endpoint = path;
            }
            
            return endpoint;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting endpoint from request", e);
            return "unknown";
        }
    }

    /**
     * Create interceptor that's enabled by default
     */
    public static ApiLatencyInterceptor create(Context context) {
        return new ApiLatencyInterceptor(true, context);
    }

    /**
     * Create interceptor with explicit enabled state
     */
    public static ApiLatencyInterceptor create(Context context, boolean enabled) {
        return new ApiLatencyInterceptor(enabled, context);
    }
} 