package com.movingtech.appmonitor.apihelper;
import android.content.Context;

import com.movingtech.appmonitor.ApiLatencyInterceptor;

import java.io.IOException;
import java.util.Map;

import okhttp3.*;

public class ApiClient {

    private static OkHttpClient client;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static void init(Context context) {
        client = new OkHttpClient.Builder()
                .addInterceptor(new ApiLatencyInterceptor(context.getApplicationContext()))
                .build();
    }

    private static OkHttpClient getClient() {
        if (client == null) {
            throw new IllegalStateException("ApiClient not initialized. Call ApiClient.init(context) first.");
        }
        return client;
    }

    public static void get(String url, Map<String, String> headers, ApiResponseCallback callback) {
        request(url, "GET", null, headers, callback);
    }

    public static void request(
            String url,
            String method,
            String jsonBody,
            Map<String, String> headers,
            ApiResponseCallback callback
    ) {
        RequestBody body = null;
        if (jsonBody != null) {
            body = RequestBody.create(jsonBody, JSON);
        }

        Request.Builder builder = new Request.Builder()
                .url(url)
                .method(method, body);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        Request request = builder.build();
        getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage(), -1);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : null;
                if (response.isSuccessful()) {
                    callback.onSuccess(responseBody);
                } else {
                    callback.onError(responseBody, response.code());
                }
            }
        });
    }
}
