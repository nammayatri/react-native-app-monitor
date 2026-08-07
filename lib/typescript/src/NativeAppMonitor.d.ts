import { type TurboModule, type CodegenTypes } from 'react-native';
export interface Spec extends TurboModule {
    initialize(configApiUrl: string, apiKey: string, userId: string): void;
    initializeWithConfig(config: {
        configApiUrl: string;
        apiKey: string;
        userId: string;
        enableNetworkMonitoring?: boolean;
    }): void;
    addEvent(eventType: string, eventName: string, eventPayload: CodegenTypes.UnsafeObject): void;
    addLog(logLevel: string, logMessage: string, tag: string, labels: {
        [key: string]: string;
    }): void;
    getSessionId(): string;
    replaceUserId(userId: string): Promise<boolean>;
    resetUserId(): void;
    generateNewSession(): string;
    getCurrentConfiguration(): string;
}
declare const _default: Spec;
export default _default;
//# sourceMappingURL=NativeAppMonitor.d.ts.map