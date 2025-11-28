import Foundation
import AppMonitor
import React

@objc(AppMonitorBridge)
class AppMonitorBridge: NSObject, RCTBridgeModule {
    
    // MARK: - React Native Module Setup
    @objc
    static func moduleName() -> String {
        return "AppMonitor"
    }
    
    // MARK: - Helper to get AppMonitor instance
    private func getAppMonitor() -> AppMonitor {
        return AppMonitor.getInstance()
    }
    
    // MARK: - React Native Methods
    
    @objc
    func addMetric(_ metricName: String?, metricValue: Double) {
        guard let metricName = metricName else { return }
        let monitor = getAppMonitor()
        _ = monitor.addMetric(metricName, value: NSNumber(value: metricValue))
    }
    
    @objc
    func addEvent(_ eventType: String?, eventName: String?, eventPayload: NSDictionary?, resolver: @escaping RCTPromiseResolveBlock, rejecter: @escaping RCTPromiseRejectBlock) {
        guard let eventType = eventType, let eventName = eventName else {
            rejecter("INVALID_PARAMS", "eventType and eventName are required", nil)
            return
        }
        
        let monitor = getAppMonitor()
        
        // Convert NSDictionary to [AnyHashable: Any] then to [String: Any]
        var payload: [String: Any] = [:]
        if let eventPayload = eventPayload {
            for (key, value) in eventPayload {
                if let stringKey = key as? String {
                    payload[stringKey] = value
                }
            }
        }
        
        _ = monitor.addEvent(eventType, eventName: eventName, eventPayload: payload)
        resolver(true)
    }
    
    @objc
    func addLog(_ logLevel: String?, logMessage: String?, tag: String?, labels: NSDictionary?) {
        let logLevelStr = logLevel ?? ""
        let logMessageStr = logMessage ?? ""
        let tagStr = tag ?? ""
        
        let monitor = getAppMonitor()
        
        // Convert NSDictionary to [AnyHashable: Any] then to [String: String]
        var stringLabels: [String: String] = [:]
        if let labels = labels {
            for (key, value) in labels {
                if let stringKey = key as? String {
                    if let stringValue = value as? String {
                        stringLabels[stringKey] = stringValue
                    } else {
                        stringLabels[stringKey] = "\(value)"
                    }
                }
            }
        }
        
        if !stringLabels.isEmpty {
            _ = monitor.addLog(logLevelStr, message: logMessageStr, tag: tagStr, labels: stringLabels)
        } else {
            _ = monitor.addLog(logLevelStr, message: logMessageStr, tag: tagStr)
        }
    }
    
    @objc
    func getSessionId(_ resolver: @escaping RCTPromiseResolveBlock, rejecter: @escaping RCTPromiseRejectBlock) {
        let monitor = getAppMonitor()
        let sessionId = monitor.getSessionId()
        resolver(sessionId)
    }
    
    @objc
    func replaceUserId(_ userId: String, resolver: @escaping RCTPromiseResolveBlock, rejecter: @escaping RCTPromiseRejectBlock) {
        let monitor = getAppMonitor()
        
        monitor.replaceUserId(userId) { success in
            if success {
                resolver(true)
            } else {
                rejecter("replace_user_error", "Failed to replace user ID", nil)
            }
        }
    }
    
    @objc
    func resetUserId() {
        let monitor = getAppMonitor()
        // Generate a new session which effectively resets the context
        monitor.generateNewSession()
    }
    
    @objc
    func generateNewSession(_ resolver: @escaping RCTPromiseResolveBlock, rejecter: @escaping RCTPromiseRejectBlock) {
        let monitor = getAppMonitor()
        monitor.generateNewSession()
        let sessionId = monitor.getSessionId()
        resolver(sessionId)
    }
}
