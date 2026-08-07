export interface EventPayload {
    [key: string]: string | number | boolean | {
        [key: string]: string | number | boolean;
    };
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
export declare class AppMonitorAPI {
    static initialize(configApiUrl: string, apiKey: string, userId: string): void;
    static initializeWithConfig(config: AppMonitorConfig): void;
    static addEvent(eventType: string, eventName: string, eventPayload?: EventPayload): void;
    static addLog(logLevel: string, logMessage: string, tag: string, labels?: Labels): void;
    static getSessionId(): string;
    static replaceUserId(userId: string): Promise<boolean>;
    static resetUserId(): void;
    static generateNewSession(): string;
    static getCurrentConfiguration(): string;
}
export default AppMonitorAPI;
//# sourceMappingURL=index.d.ts.map