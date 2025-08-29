package com.example.smssender;

/**
 * Application Configuration
 * 
 * This is the main configuration file for the SMS Sender application.
 * Update these values when deploying for a new project or client.
 * 
 * BACKEND STATUS: ✅ CONFIRMED READY (Dec 28, 2024)
 * - Reply handler endpoint: Fully implemented
 * - Serbian language support: Active
 * - Monitoring dashboard: https://srb.trackify.net/api/sms/monitor.html
 * 
 * IMPORTANT: After making changes to this file, you must rebuild the APK:
 * ./gradlew clean assembleDebug (for debug version)
 * ./gradlew clean assembleRelease (for release version)
 */
public class AppConfig {
    
    // ============================================================================
    // PROJECT CONFIGURATION - UPDATE THESE VALUES FOR EACH NEW PROJECT
    // ============================================================================
    
    /**
     * Application Display Name
     * This appears in the app UI and notifications
     */
    public static final String APP_NAME = "MEX SMS Gateway";
    
    /**
     * Base URL for API endpoints
     * Do NOT include trailing slash
     * Example: "https://yourdomain.com" or "https://subdomain.yourdomain.com"
     */
    public static final String BASE_URL = "https://mex.mk";
    
    /**
     * API Authentication Key
     * This key is sent with all API requests in the X-API-Key header
     */
    public static final String API_KEY = "osafu2379jsaf";
    
    /**
     * Default phone number for the device (optional)
     * Used as the "from" number in reply handling
     * Format: +[country code][number] (e.g., "+381600000000")
     */
    public static final String DEFAULT_PHONE_NUMBER = "+381600000000";
    
    // ============================================================================
    // API ENDPOINTS - These are automatically constructed from BASE_URL
    // ============================================================================
    
    /**
     * SMS Prober endpoint - for fetching SMS tasks
     * GET request to fetch tasks, POST request to update status
     */
    public static final String PROBE_ENDPOINT = BASE_URL + "/api/sms/prober.php";
    
    /**
     * Reply Handler endpoint - for sending received SMS replies to server
     * POST request with reply data
     */
    public static final String REPLY_ENDPOINT = BASE_URL + "/api/sms/reply_handler.php";
    
    // ============================================================================
    // TIMING CONFIGURATION
    // ============================================================================
    
    /**
     * Default probe interval in seconds
     * How often the app checks for new SMS tasks
     */
    public static final int DEFAULT_PROBE_INTERVAL = 60; // seconds
    
    /**
     * Minimum allowed probe interval in seconds
     * Prevents users from setting too aggressive polling
     */
    public static final int MIN_PROBE_INTERVAL = 10; // seconds
    
    /**
     * Maximum allowed probe interval in seconds
     */
    public static final int MAX_PROBE_INTERVAL = 3600; // 1 hour
    
    /**
     * Reply batch interval in milliseconds
     * How often queued replies are sent to server
     */
    public static final long REPLY_BATCH_INTERVAL = 30000; // 30 seconds
    
    /**
     * Reply batch size
     * Maximum number of replies to send in one batch
     */
    public static final int REPLY_BATCH_SIZE = 10;
    
    /**
     * SMS delivery timeout in seconds
     * After this time, SMS is marked as sent_unconfirmed if no delivery report
     */
    public static final int SMS_DELIVERY_TIMEOUT = 30; // seconds
    
    // ============================================================================
    // NETWORK CONFIGURATION
    // ============================================================================
    
    /**
     * HTTP connection timeout in milliseconds
     */
    public static final int CONNECTION_TIMEOUT = 10000; // 10 seconds
    
    /**
     * HTTP read timeout in milliseconds
     */
    public static final int READ_TIMEOUT = 15000; // 15 seconds
    
    // ============================================================================
    // LOGGING AND DEBUGGING
    // ============================================================================
    
    /**
     * Enable debug logging
     * Set to false in production builds
     */
    public static final boolean DEBUG_MODE = true;
    
    /**
     * Maximum log entries to keep in memory
     */
    public static final int MAX_LOG_ENTRIES = 100;
    
    /**
     * Maximum number of recent phone numbers to track for reply correlation
     */
    public static final int MAX_TRACKED_NUMBERS = 100;
    
    // ============================================================================
    // UI CONFIGURATION
    // ============================================================================
    
    /**
     * App subtitle shown in the main screen
     */
    public static final String APP_SUBTITLE = "MEX Automated SMS Service";
    
    /**
     * Notification channel name
     */
    public static final String NOTIFICATION_CHANNEL_NAME = "SMS Probe Service Channel";
    
    /**
     * Notification channel description
     */
    public static final String NOTIFICATION_CHANNEL_DESC = "Continuous SMS probing service";
    
    // ============================================================================
    // FEATURE FLAGS
    // ============================================================================
    
    /**
     * Enable SMS reply handling
     * Set to false if you don't want to capture and process incoming SMS
     */
    public static final boolean ENABLE_REPLY_HANDLING = true;
    
    /**
     * Enable automatic opt-out processing
     * When true, STOP messages automatically add numbers to opt-out list
     */
    public static final boolean ENABLE_AUTO_OPT_OUT = true;
    
    /**
     * Enable PIN verification
     * When true, the app will verify PIN codes in replies
     */
    public static final boolean ENABLE_PIN_VERIFICATION = true;
    
    // ============================================================================
    // VALIDATION RULES
    // ============================================================================
    
    /**
     * Maximum SMS message length
     */
    public static final int MAX_SMS_LENGTH = 160;
    
    /**
     * Reply message maximum length for server
     */
    public static final int MAX_REPLY_LENGTH = 1000;
    
    // ============================================================================
    // DO NOT MODIFY BELOW THIS LINE
    // ============================================================================
    
    /**
     * Application version
     * This is automatically updated during build
     */
    public static final String APP_VERSION = "1.0.0";
    
    /**
     * Build timestamp
     * Automatically set during compilation
     */
    public static final String BUILD_TIME = String.valueOf(System.currentTimeMillis());
    
    /**
     * Validates the configuration
     * Called during app initialization to ensure config is valid
     */
    public static boolean validateConfig() {
        if (BASE_URL == null || BASE_URL.isEmpty()) {
            return false;
        }
        if (API_KEY == null || API_KEY.isEmpty()) {
            return false;
        }
        if (DEFAULT_PROBE_INTERVAL < MIN_PROBE_INTERVAL || 
            DEFAULT_PROBE_INTERVAL > MAX_PROBE_INTERVAL) {
            return false;
        }
        return true;
    }
    
    /**
     * Gets a human-readable configuration summary
     */
    public static String getConfigSummary() {
        return "App: " + APP_NAME + "\n" +
               "API: " + BASE_URL + "\n" +
               "Probe Interval: " + DEFAULT_PROBE_INTERVAL + "s\n" +
               "Reply Handling: " + (ENABLE_REPLY_HANDLING ? "Enabled" : "Disabled") + "\n" +
               "Version: " + APP_VERSION;
    }
}