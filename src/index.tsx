import AppMonitor from './NativeAppMonitor';

export interface EventPayload {
  [key: string]:
    | string
    | number
    | boolean
    | { [key: string]: string | number | boolean };
}

export interface Labels {
  [key: string]: string;
}

export interface AppMonitorConfig {
  configApiUrl: string;
  apiKey: string;
  userId: string;
  enableNetworkMonitoring?: boolean;
}

export class AppMonitorAPI {
  static initialize(
    configApiUrl: string,
    apiKey: string,
    userId: string
  ): void {
    AppMonitor.initialize(configApiUrl, apiKey, userId);
  }

  static initializeWithConfig(config: AppMonitorConfig): void {
    AppMonitor.initializeWithConfig(config);
  }

  static addMetric(metricName: string, metricValue: number): void {
    AppMonitor.addMetric(metricName, metricValue);
  }

  static addEvent(
    eventType: string,
    eventName: string,
    eventPayload: EventPayload = {}
  ): void {
    AppMonitor.addEvent(eventType, eventName, eventPayload);
  }

  static addLog(
    logLevel: string,
    logMessage: string,
    tag: string,
    labels: Labels = {}
  ): void {
    AppMonitor.addLog(logLevel, logMessage, tag, labels);
  }

  static getSessionId(): string {
    return AppMonitor.getSessionId();
  }

  static async replaceUserId(userId: string): Promise<boolean> {
    return AppMonitor.replaceUserId(userId);
  }

  static resetUserId(): void {
    AppMonitor.resetUserId();
  }

  static generateNewSession(): string {
    return AppMonitor.generateNewSession();
  }

  static getCurrentConfiguration(): string {
    return AppMonitor.getCurrentConfiguration();
  }
}

export default AppMonitorAPI;
