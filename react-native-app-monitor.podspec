require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-app-monitor"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.description  = <<-DESC
                  Comprehensive monitoring solution for React Native applications with metrics, events, logs, and API latency tracking
                   DESC
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.author       = package["author"]
  s.platforms    = { :ios => "13.4" }
  s.source       = { :git => package["repository"]["url"], :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift}"
  s.public_header_files = "ios/*.h"
  s.requires_arc = true
  
  # Include the generated codegen files
  s.pod_target_xcconfig = {
    "HEADER_SEARCH_PATHS" => "\"$(PODS_ROOT)/Headers/Private/React-Core\" \"$(PODS_TARGET_SRCROOT)/ios\""
  }

  # React Native
  s.dependency "React-Core"
  s.dependency "ReactCommon/turbomodule/core"

  # AppMonitor iOS SDK - from private spec repo
  s.dependency "AppMonitor", "~> 1.0.0"

  # Swift version
  s.swift_version = "5.0"
end

