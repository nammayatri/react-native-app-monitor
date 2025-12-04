import { TurboModuleRegistry, type TurboModule } from 'react-native';

export interface Spec extends TurboModule {
  addMetric(metricName: string, metricValue: number): void;
  addEvent(
    eventType: string,
    eventName: string,
    eventPayload: {
      [key: string]:
        | string
        | number
        | boolean
        | { [key: string]: string | number | boolean };
    }
  ): void;
  addLog(
    logLevel: string,
    logMessage: string,
    tag: string,
    labels: { [key: string]: string }
  ): void;
  getSessionId(): string;
  replaceUserId(userId: string): Promise<boolean>;
  resetUserId(): void;
  generateNewSession(): string;
}

export default TurboModuleRegistry.getEnforcing<Spec>('AppMonitor');
