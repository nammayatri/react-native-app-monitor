import NativeAppMonitor from './NativeAppMonitor';

export interface EventPayload {
    [key: string]: string | number | boolean | { [key: string]: string | number | boolean };
}

export interface Labels {
    [key: string]: string;
}

/**
 * AppMonitor - Comprehensive monitoring solution for React Native applications
 * 
 * Features:
 * - Metrics collection and tracking
 * - Event logging with structured data
 * - Application logging with levels and tags
 * - Session management
 * - User ID tracking
 * - API latency monitoring (automatic via interceptor)
 * 
 * @example
 * ```typescript
 * import AppMonitor from 'react-native-app-monitor';
 * 
 * // Add a metric
 * AppMonitor.addMetric('screen_load_time', 1250);
 * 
 * // Track an event
 * AppMonitor.addEvent('user_action', 'button_click', {
 *   screen: 'home',
 *   button_id: 'login'
 * });
 * 
 * // Add a log
 * AppMonitor.addLog('INFO', 'User logged in', 'AUTH', {
 *   userId: '123',
 *   method: 'email'
 * });
 * 
 * // Get session ID
 * const sessionId = AppMonitor.getSessionId();
 * 
 * // Replace user ID
 * await AppMonitor.replaceUserId('new_user_id');
 * 
 * // Generate new session
 * const newSessionId = AppMonitor.generateNewSession();
 * ```
 */
class AppMonitor {
    /**
     * Add a metric value
     * @param metricName - Name of the metric
     * @param metricValue - Numeric value of the metric
     */
    static addMetric(metricName: string, metricValue: number): void {
        NativeAppMonitor.addMetric(metricName, metricValue);
    }

    /**
     * Add an event with structured data
     * @param eventType - Type/category of the event (e.g., 'user_action', 'system_event')
     * @param eventName - Name of the event (e.g., 'button_click', 'screen_view')
     * @param eventPayload - Event data as key-value pairs
     */
    static addEvent(eventType: string, eventName: string, eventPayload: EventPayload): void {
        try {
            NativeAppMonitor.addEvent(eventType, eventName, eventPayload);
        } catch (error) {
            console.error('Error adding event:', error);
        }
    }

    /**
     * Add a log entry
     * @param logLevel - Log level (e.g., 'DEBUG', 'INFO', 'WARN', 'ERROR')
     * @param logMessage - Log message
     * @param tag - Log tag for categorization
     * @param labels - Optional key-value labels for additional context
     */
    static addLog(logLevel: string, logMessage: string, tag: string, labels?: Labels): void {
        try {
            NativeAppMonitor.addLog(logLevel, logMessage, tag, labels ?? {});
        } catch (error) {
            console.error('Error adding log:', error);
        }
    }

    /**
     * Get the current session ID
     * @returns Current session ID string
     */
    static getSessionId(): string {
        try {
            return NativeAppMonitor.getSessionId();
        } catch (error) {
            console.error('Error getting session ID:', error);
            return '';
        }
    }

    /**
     * Replace the current user ID with a new one
     * Updates all tracking to use the new user ID
     * @param userId - New user ID
     * @returns Promise that resolves to true if successful
     */
    static replaceUserId(userId: string): Promise<boolean> {
        return NativeAppMonitor.replaceUserId(userId);
    }

    /**
     * Reset the user ID to a random UUID
     * Useful for logout scenarios
     */
    static resetUserId(): void {
        NativeAppMonitor.resetUserId();
    }

    /**
     * Generate a new session ID
     * Creates a fresh session for tracking
     * @returns New session ID string
     */
    static generateNewSession(): string {
        return NativeAppMonitor.generateNewSession();
    }
}

export default AppMonitor;
export { AppMonitor };
