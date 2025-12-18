#import "AppMonitor.h"

#if __has_include(<RNAppMonitor/RNAppMonitor-Swift.h>)
#import <RNAppMonitor/RNAppMonitor-Swift.h>
#else
#import "RNAppMonitor-Swift.h"
#endif

@implementation AppMonitor {
    RNAppMonitorBridge *_bridge;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _bridge = [RNAppMonitorBridge getInstance];
    }
    return self;
}

- (void)initialize:(NSString *)configApiUrl apiKey:(NSString *)apiKey userId:(NSString *)userId {
    [_bridge initialize:configApiUrl apiKey:apiKey userId:userId];
}

- (void)initializeWithConfig:(NSDictionary *)config {
    [_bridge initializeWithConfig:config];
}

- (void)addMetric:(NSString *)metricName metricValue:(double)metricValue {
    [_bridge addMetric:metricName value:@(metricValue)];
}

- (void)addEvent:(NSString *)eventType
       eventName:(NSString *)eventName
    eventPayload:(NSDictionary *)eventPayload {
    [_bridge addEvent:eventType eventName:eventName eventPayload:eventPayload];
}

- (void)addLog:(NSString *)logLevel
    logMessage:(NSString *)logMessage
           tag:(NSString *)tag
        labels:(NSDictionary<NSString *, NSString *> *)labels {
    [_bridge addLog:logLevel message:logMessage tag:tag labels:labels];
}

- (NSString *)getSessionId {
    return [_bridge getSessionId];
}

- (void)replaceUserId:(NSString *)userId
              resolve:(RCTPromiseResolveBlock)resolve
               reject:(RCTPromiseRejectBlock)reject {
    [_bridge replaceUserId:userId completion:^(BOOL success) {
        if (success) {
            resolve(@YES);
        } else {
            reject(@"replace_user_error", @"Failed to replace user ID", nil);
        }
    }];
}

- (void)resetUserId {
    [_bridge resetUserId];
}

- (NSString *)generateNewSession {
    return [_bridge generateNewSession];
}

- (NSString *)getCurrentConfiguration {
    return [_bridge getCurrentConfiguration];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeAppMonitorSpecJSI>(params);
}

+ (NSString *)moduleName {
    return @"AppMonitor";
}

@end
