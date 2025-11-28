#import <React/RCTBridgeModule.h>
#import <React/RCTViewManager.h>
#import "AppMonitorBridge.h"

@interface RCT_EXTERN_MODULE(AppMonitorBridge, NSObject)

RCT_EXTERN_METHOD(addMetric:(NSString *)metricName
                  metricValue:(double)metricValue)

RCT_EXTERN_METHOD(addEvent:(NSString *)eventType
                  eventName:(NSString *)eventName
                  eventPayload:(NSDictionary *)eventPayload
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(addLog:(NSString *)logLevel
                  logMessage:(NSString *)logMessage
                  tag:(NSString *)tag
                  labels:(NSDictionary *)labels)

RCT_EXTERN_METHOD(getSessionId:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(replaceUserId:(NSString *)userId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(resetUserId)

RCT_EXTERN_METHOD(generateNewSession:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

+ (BOOL)requiresMainQueueSetup
{
    return NO;
}

@end
