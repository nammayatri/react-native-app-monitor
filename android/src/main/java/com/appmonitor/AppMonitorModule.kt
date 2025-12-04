package com.appmonitor

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import com.movingtech.appmonitor.AppMonitor
import java.util.UUID

@ReactModule(name = AppMonitorModule.NAME)
class AppMonitorModule(reactContext: ReactApplicationContext) :
  NativeAppMonitorSpec(reactContext) {

  private val appMonitor: AppMonitor = AppMonitor.getInstance(reactContext)

  override fun getName(): String {
    return NAME
  }

  override fun addMetric(metricName: String?, metricValue: Double) {
    appMonitor.addMetric(metricName, metricValue)
  }

  override fun addEvent(
    eventType: String?,
    eventName: String?,
    eventPayload: ReadableMap?,
  ) {
    if (eventName == null || eventType == null) return
    val map: Map<String, Any?> = eventPayload?.toHashMap() ?: emptyMap()
    appMonitor.addEvent(eventType, eventName, map)
  }

  override fun addLog(
    logLevel: String?,
    logMessage: String?,
    tag: String?,
    labels: ReadableMap?,
  ) {
    if (labels == null) {
      appMonitor.addLog(logLevel, logMessage, tag)
    } else {
      val labelsMap: Map<String, String> = readableMapToStringMap(labels)
      appMonitor.addLog(logLevel, logMessage, tag, labelsMap)
    }
  }

  override fun getSessionId(): String {
    return appMonitor.sessionId
  }

  override fun replaceUserId(userId: String, promise: Promise) {
    val success = appMonitor.replaceUserId(userId).get() // This will block until the API call completes
    promise.resolve(success)
  }

  override fun resetUserId() {
    appMonitor.setUserId(UUID.randomUUID().toString())
  }

  override fun generateNewSession(): String {
    appMonitor.generateNewSession()
    return appMonitor.sessionId
  }

  private fun readableMapToStringMap(readableMap: ReadableMap?): Map<String, String> {
    if (readableMap == null) return emptyMap()

    val hashMap = readableMap.toHashMap()
    val stringMap = mutableMapOf<String, String>()

    for ((key, value) in hashMap) {
      stringMap[key] = when (value) {
        is String -> value
        is Boolean -> value.toString()
        is Number -> value.toString()
        is Map<*, *> -> value.toString()
        is List<*> -> value.toString()
        null -> ""
        else -> value.toString()
      }
    }

    return stringMap
  }

  companion object {
    const val NAME = "AppMonitor"
  }
}
