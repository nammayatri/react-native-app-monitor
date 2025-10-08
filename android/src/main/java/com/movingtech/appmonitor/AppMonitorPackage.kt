package com.movingtech.appmonitor

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class AppMonitorPackage : TurboReactPackage() {
    
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        return if (name == AppMonitorBridge.NAME) {
            AppMonitorBridge(reactContext)
        } else {
            null
        }
    }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider {
            val moduleInfos: MutableMap<String, ReactModuleInfo> = HashMap()
            moduleInfos[AppMonitorBridge.NAME] = ReactModuleInfo(
                AppMonitorBridge.NAME,
                AppMonitorBridge.NAME,
                false,  // canOverrideExistingModule
                false,  // needsEagerInit
                false,  // isCxxModule
                true    // isTurboModule
            )
            moduleInfos
        }
    }
}
