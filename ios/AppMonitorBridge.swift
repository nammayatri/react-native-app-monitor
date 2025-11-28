import Foundation
import AppMonitor
import React

// TurboModule implementation for New Architecture
// RNAppMonitorSpec protocol is defined in RNAppMonitorSpec.h (forward declaration)
// Codegen will generate the actual protocol and base class at app build time
@objc(AppMonitorBridge)
class AppMonitorBridge: NSObject {
    
    // MARK: - Helper to get AppMonitor instance
    private func getAppMonitor() -> AppMonitor {
        return AppMonitor.getInstance()
    }
    
    // MARK: - TurboModule Methods (matching NativeAppMonitorSpec interface)
    // These methods will be automatically bridged by React Native codegen
    
    func addMetric(_ metricName: String, metricValue: Double) {
        let monitor = getAppMonitor()
        _ = monitor.addMetric(metricName, value: NSNumber(value: metricValue))
    }
    
    func addEvent(_ eventType: String, eventName: String, eventPayload: [String: Any]) {
        let monitor = getAppMonitor()
        _ = monitor.addEvent(eventType, eventName: eventName, eventPayload: eventPayload)
    }
    
    func addLog(_ logLevel: String, logMessage: String, tag: String, labels: [String: String]) {
        let monitor = getAppMonitor()
        if !labels.isEmpty {
            _ = monitor.addLog(logLevel, message: logMessage, tag: tag, labels: labels)
        } else {
            _ = monitor.addLog(logLevel, message: logMessage, tag: tag)
        }
    }
    
    func getSessionId() -> String {
        let monitor = getAppMonitor()
        return monitor.getSessionId()
    }
    
    func replaceUserId(_ userId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let monitor = getAppMonitor()
        monitor.replaceUserId(userId) { success in
            if success {
                resolve(true)
            } else {
                reject("replace_user_error", "Failed to replace user ID", nil)
            }
        }
    }
    
    func resetUserId() {
        let monitor = getAppMonitor()
        monitor.generateNewSession()
    }
    
    func generateNewSession() -> String {
        let monitor = getAppMonitor()
        monitor.generateNewSession()
        return monitor.getSessionId()
    }
}
