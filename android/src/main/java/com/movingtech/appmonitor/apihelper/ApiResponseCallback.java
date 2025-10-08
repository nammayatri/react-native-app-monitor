package com.movingtech.appmonitor.apihelper;

public interface ApiResponseCallback {
    void onSuccess(String responseBody);
    void onError(String errorMessage, int code);
}