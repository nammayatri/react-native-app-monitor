import Foundation
import AppMonitor

@objc(RNAppMonitorBridge)
public class RNAppMonitorBridge: NSObject {
    
    private let sdk = AppMonitor.getInstance()
    
    @objc
    public static func getInstance() -> RNAppMonitorBridge {
        return RNAppMonitorBridge()
    }
    
    @objc
    public func initialize(_ configApiUrl: String, apiKey: String, userId: String) {
        sdk.initialize(configApiUrl, apiKey: apiKey, userId: userId)
    }
    
    @objc
    public func initializeWithConfig(_ config: NSDictionary) {
        guard let configApiUrl = config["configApiUrl"] as? String,
              let apiKey = config["apiKey"] as? String,
              let userId = config["userId"] as? String else {
            return
        }
        
        let enableNetworkMonitoring = config["enableNetworkMonitoring"] as? Bool ?? false
        
        sdk.initialize(configApiUrl, apiKey: apiKey, userId: userId)
        
        // Setup network monitoring if enabled
        if enableNetworkMonitoring {
            // sdk.setupNetworkMonitoring()
            // TODO: Check for IOS
        }
    }
    
    @objc
    public func addMetric(_ metricName: String, value: NSNumber) {
        _ = sdk.addMetric(metricName, value: value)
    }
    
    @objc
    public func addEvent(_ eventType: String, eventName: String, eventPayload: NSDictionary) {
        let payload = eventPayload as? [String: Any] ?? [:]
        _ = sdk.addEvent(eventType, eventName: eventName, eventPayload: payload)
    }
    
    @objc
    public func addLog(_ logLevel: String, message: String, tag: String, labels: NSDictionary?) {
        if let labels = labels as? [String: String], !labels.isEmpty {
            _ = sdk.addLog(logLevel, message: message, tag: tag, labels: labels)
        } else {
            _ = sdk.addLog(logLevel, message: message, tag: tag)
        }
    }
    
    @objc
    public func getSessionId() -> String {
        return sdk.getSessionId()
    }
    
    @objc
    public func replaceUserId(_ userId: String, completion: @escaping (Bool) -> Void) {
        sdk.replaceUserId(userId, completion: completion)
    }
    
    @objc
    public func resetUserId() {
        sdk.generateNewSession()
    }
    
    @objc
    public func generateNewSession() -> String {
        sdk.generateNewSession()
        return sdk.getSessionId()
    }

    @objc
    public func getCurrentConfiguration() -> String {
        let config = sdk.getCurrentConfiguration()
        if let jsonData = try? JSONSerialization.data(withJSONObject: config, options: []),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            return jsonString
        }
        return "{}"
    }
}

