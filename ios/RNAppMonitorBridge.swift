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
}

