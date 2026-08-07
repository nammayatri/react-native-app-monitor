"use strict";

import AppMonitor from './NativeAppMonitor';
export class AppMonitorAPI {
  static initialize(configApiUrl, apiKey, userId) {
    AppMonitor.initialize(configApiUrl, apiKey, userId);
  }
  static initializeWithConfig(config) {
    AppMonitor.initializeWithConfig(config);
  }
  static addEvent(eventType, eventName, eventPayload = {}) {
    AppMonitor.addEvent(eventType, eventName, eventPayload);
  }
  static addLog(logLevel, logMessage, tag, labels = {}) {
    AppMonitor.addLog(logLevel, logMessage, tag, labels);
  }
  static getSessionId() {
    return AppMonitor.getSessionId();
  }
  static async replaceUserId(userId) {
    return AppMonitor.replaceUserId(userId);
  }
  static resetUserId() {
    AppMonitor.resetUserId();
  }
  static generateNewSession() {
    return AppMonitor.generateNewSession();
  }
  static getCurrentConfiguration() {
    return AppMonitor.getCurrentConfiguration();
  }
}
export default AppMonitorAPI;
//# sourceMappingURL=index.js.map