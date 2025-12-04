#ifdef __cplusplus
#import <AppMonitorSpec/AppMonitorSpec.h>
#endif

#import <React/RCTBridgeModule.h>

#ifdef __cplusplus
@interface AppMonitor : NSObject <NativeAppMonitorSpec>
#else
@interface AppMonitor : NSObject <RCTBridgeModule>
#endif

@end
