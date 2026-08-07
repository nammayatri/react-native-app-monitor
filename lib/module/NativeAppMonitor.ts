import {
  TurboModuleRegistry,
  type TurboModule,
  type CodegenTypes,
} from 'react-native';

export interface Spec extends TurboModule {
  initialize(configApiUrl: string, apiKey: string, userId: string): void;
  initializeWithConfig(config: {
    configApiUrl: string;
    apiKey: string;
    userId: string;
    enableNetworkMonitoring?: boolean;
  }): void;
  addEvent(
    eventType: string,
    eventName: string,
    // RN 0.86 codegen rejects an object literal nested inside a union (TSTypeLiteral
    // in emitUnion), so the previous inline payload type fails to build. The native
    // implementations accept ReadableMap / NSDictionary, so UnsafeObject is the
    // faithful spec type; callers should validate/sanitise payloads before this call.
    eventPayload: CodegenTypes.UnsafeObject
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
  getCurrentConfiguration(): string;
}

export default TurboModuleRegistry.getEnforcing<Spec>('AppMonitor');
