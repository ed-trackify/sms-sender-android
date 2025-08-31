# CLAUDE.md - SMS Gateway Android App

This file provides comprehensive guidance to Claude Code (claude.ai/code) when working with this SMS gateway Android application.

## 🚀 Quick Start for New Projects

### To Deploy for a New Client/Project:
1. **Update Configuration**: Edit `app/src/main/java/com/example/smssender/AppConfig.java`
   - Set `APP_NAME` to your app name
   - Set `BASE_URL` to your API domain (without trailing slash)
   - Set `API_KEY` to your authentication key
   - Adjust timing intervals if needed

2. **Rebuild APK**:
   ```bash
   # For debug version
   export JAVA_HOME="$(pwd)/jdk-17" && ./gradlew clean assembleDebug
   
   # For release version
   export JAVA_HOME="$(pwd)/jdk-17" && ./gradlew clean assembleRelease
   ```

3. **Find APK**: Located at `app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Repository Overview

This is a **fully-featured Android SMS gateway application** that:
- Automatically sends SMS messages based on instructions from a remote API
- Captures and processes SMS replies from recipients
- Provides real-time status tracking and delivery confirmations
- Runs continuously in the background with robust error handling
- Supports batch processing for efficiency

### Core Capabilities:
1. **Outbound SMS**: Polls API endpoint, sends SMS, tracks delivery
2. **Inbound SMS**: Captures replies, classifies them, sends to server
3. **Background Operation**: Runs 24/7 with wake locks and foreground service
4. **Status Tracking**: Real-time delivery confirmations and batch updates
5. **Reply Intelligence**: Automatic classification (PIN, opt-out, inquiries, etc.)

---

## 🛠️ Development Environment

### Prerequisites:
- **JDK 17**: Required (JDK 21/24 NOT compatible due to D8 dexer issues)
  - Linux JDK included in repo: `jdk-17/` or `openlogic-openjdk-17.0.10+7-linux-x64/`
  - Windows JDK (not usable in WSL): `jdk-17.0.2/`
  - Note: JDK 21 (`jdk-21.0.8/`) and JDK 24 (`jdk-24.0.2/`) are present but NOT compatible
- **Android SDK**: API 34 (Android 14)
- **Gradle**: 8.13 (wrapper included)

### Build Configuration:
```gradle
android {
    compileSdk 34
    minSdk 23        // Android 6.0+
    targetSdk 34     // Android 14
}
```

### Common Development Commands:
```bash
# Set Java environment (required each session)
export JAVA_HOME="$(pwd)/jdk-17"
export PATH=$JAVA_HOME/bin:$PATH

# Build APK
./gradlew assembleDebug      # Debug version
./gradlew assembleRelease    # Release version

# Clean build
./gradlew clean

# Run tests
./gradlew test
./gradlew connectedAndroidTest
```

---

## 🏗️ Code Architecture

### Configuration System (`AppConfig.java`)
Central configuration file for easy customization:
- **API Settings**: Base URL, endpoints, API key
- **Timing**: Probe intervals, batch sizes, timeouts
- **Features**: Reply handling, PIN verification, opt-out processing
- **UI**: App name, subtitle, notifications

### Core Components

#### 1. **MainActivity.java** (`app/src/main/java/com/example/smssender/`)
- Main UI with Material Design interface
- Service management (start/stop background service)
- Real-time statistics display (SMS sent/failed/pending)
- Reply statistics (total replies, queue size, PIN confirmations)
- Permission handling for SMS and background operation
- Dynamic configuration from AppConfig

#### 2. **SmsProbeService.java** (`app/src/main/java/com/example/smssender/`)
- **Background Service**: Runs continuously with foreground notification
- **API Polling**: Configurable intervals (default 60 seconds)
- **SMS Sending**: With delivery tracking via PendingIntents
- **Status Updates**: Batch processing for efficiency
- **Wake Lock**: Keeps service running when screen off
- **Shipment Tracking**: Stores data for reply correlation

**API Flow**:
```
GET /api/sms/prober.php → Receive SMS task → Send SMS → Track delivery → POST status update
```

#### 3. **SmsReceiver.java** (`app/src/main/java/com/example/smssender/`)
- **BroadcastReceiver**: Intercepts all incoming SMS
- **Reply Classification**:
  - PIN confirmation (`PIN: 1234`)
  - Opt-out requests (`STOP`, `UNSUBSCRIBE`)
  - Delivery confirmations (`DELIVERED`, `RECEIVED`)
  - Reschedule requests (`POSTPONE`, `DELAY`)
  - Complaints (`PROBLEM`, `ISSUE`)
  - General inquiries (questions)
- **Shipment Correlation**: Links replies to original SMS
- **Queue Management**: Sends replies to ReplyQueueManager

#### 4. **ReplyQueueManager.java** (`app/src/main/java/com/example/smssender/`)
- **Singleton Pattern**: Single instance manages all replies
- **Persistent Queue**: Survives app restarts using SharedPreferences
- **Batch Processing**: Groups up to 10 replies or sends every 30 seconds
- **Retry Logic**: Re-queues failed transmissions
- **Statistics**: Tracks success/failure counts

**Reply Flow**:
```
Receive SMS → Classify → Queue → Batch → POST /api/sms/reply_handler.php
```

#### 5. **ServiceRestartReceiver.java** (`app/src/main/java/com/example/smssender/`)
- **BroadcastReceiver**: Handles service restart events
- **Boot Persistence**: Automatically starts service on device boot
- **Custom Restart**: Responds to `com.example.smssender.RESTART_SERVICE` action
- **Android O+ Support**: Uses `startForegroundService()` for Android 8.0+
- **Error Handling**: Logs failures and continues gracefully

---

## 📡 API Integration

### Authentication
All requests include header: `X-API-Key: osafu2379jsaf`

### Endpoints

#### 1. SMS Prober (`/api/sms/prober.php`)

**GET Request** - Fetch SMS tasks:
```json
Response:
{
  "phone": "+1234567890",
  "message": "Your package will arrive today. PIN: 4567",
  "queue_id": 789,
  "shipment_id": 12345
}
```

**POST Request** - Status updates:
```json
Single update:
{
  "queue_id": 789,
  "phone": "+1234567890",
  "status": "delivered",
  "sent_timestamp": 1703123456000,
  "delivered_timestamp": 1703123466000,
  "delivery_time_seconds": 10
}

Batch updates:
[
  {"queue_id": 789, "status": "sent", ...},
  {"queue_id": 790, "status": "delivered", ...}
]
```

Status progression: `processing` → `pending` → `sent` → `delivered`/`failed`/`sent_unconfirmed`

#### 2. Reply Handler (`/api/sms/reply_handler.php`)

**POST Request** - Send replies to server:
```json
{
  "phone_from": "+1234567890",
  "phone_to": "+381600000000",
  "message": "PIN: 4567",
  "received_timestamp": 1703123456000,
  "shipment_id": 12345,
  "original_queue_id": 789,
  "reply_type": "pin_confirmation",
  "device_info": {
    "android_version": "13",
    "app_version": "1.0.0",
    "device_id": "AND_1703123456_device"
  }
}
```

### Response Codes:
- `200`: Success
- `204`: No content (no SMS tasks)
- `401`: Authentication failed
- `429`: Rate limit exceeded

---

## 🔒 Permissions

### Required Permissions:
```xml
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

---

## 🎨 UI Components

### Material Design Interface:
- **Status Cards**: System status, permissions, network
- **Statistics Dashboard**: Real-time SMS and reply counts
- **Reply Statistics**: Queue size, PIN confirmations, sync status
- **Activity Log**: Scrollable log with timestamps
- **Configuration**: Probe interval setting

### Color Scheme:
- Primary: `#6200EE` (Purple)
- Success: `#4CAF50` (Green)
- Error: `#F44336` (Red)
- Pending: `#FF9800` (Orange)

---

## 📊 Data Persistence

### SharedPreferences Storage:
- **SmsProbeLog**: Activity logs
- **ReplyQueue**: Queued replies for server
- **ReplyStats**: Statistics counters
- **ShipmentTracking**: Links phone numbers to shipments
- **SmsRecipients**: Recent SMS recipients
- **AppConfig**: Runtime configuration

---

## 🐛 Troubleshooting

### Common Issues:

1. **JDK Compatibility Error**:
   ```
   ERROR: D8: NullPointerException
   ```
   **Solution**: Use JDK 17, not JDK 21/24

2. **SMS Not Sending**:
   - Check SMS permissions granted
   - Verify SIM card present
   - Check airplane mode disabled

3. **Replies Not Captured**:
   - Verify RECEIVE_SMS permission
   - Check if app is default SMS app (optional)
   - Ensure service is running

4. **API Connection Failed**:
   - Verify BASE_URL in AppConfig
   - Check API_KEY is correct
   - Ensure device has internet

---

## 🚀 Deployment Checklist

### For New Deployment:
- [ ] Update AppConfig.java with client details
- [ ] Set correct BASE_URL (no trailing slash)
- [ ] Configure API_KEY
- [ ] Adjust probe intervals if needed
- [ ] Update app name and subtitle
- [ ] Build APK (debug or release)
- [ ] Test on target device
- [ ] Verify SMS sending works
- [ ] Confirm reply capture works
- [ ] Check background operation

### Backend Requirements:
- [ ] `/api/sms/prober.php` endpoint ready
- [ ] `/api/sms/reply_handler.php` endpoint ready
- [ ] API key authentication configured
- [ ] Database tables created
- [ ] Status tracking implemented

---

## 📁 Project Structure

```
sms-sender-android/
├── app/
│   ├── src/main/java/com/example/smssender/
│   │   ├── AppConfig.java              # Central configuration
│   │   ├── MainActivity.java           # Main UI
│   │   ├── SmsProbeService.java        # Background service
│   │   ├── SmsReceiver.java            # Reply handler
│   │   ├── ReplyQueueManager.java      # Reply queue
│   │   └── ServiceRestartReceiver.java # Service restart handler
│   ├── src/main/res/
│   │   ├── layout/
│   │   │   └── activity_main.xml       # UI layout
│   │   └── values/
│   │       ├── colors.xml
│   │       └── strings.xml
│   └── build.gradle                     # App build config
├── jdk-17/                              # Linux JDK (symbolic link)
├── openlogic-openjdk-17.0.10+7-linux-x64/ # Full Linux JDK
├── jdk-17.0.2/                          # Windows JDK (not for WSL)
├── gradle/                              # Gradle wrapper
├── build.gradle                         # Project build config
└── CLAUDE.md                            # This file
```

---

## 🔄 Version History

### Current Version: 1.0.0 (Enhanced)
- Full SMS gateway functionality with robust background operation
- Reply capture and classification with intelligent routing
- Background service with wake locks and auto-restart capability
- Service restart receiver for boot persistence
- Batch processing for efficiency (up to 10 items or 30-second intervals)
- Material Design UI with real-time statistics
- Configuration system for easy deployment
- Enhanced error handling and retry logic
- Support for mex.mk deployment configuration

### Known Limitations:
- Maximum 160 characters per SMS (standard SMS limit)
- Batch size limited to 10 replies
- 30-second delivery confirmation timeout

---

## 💡 Important Notes

1. **Security**: Never commit API keys to public repos
2. **Battery**: App requests battery optimization exemption for reliability
3. **Permissions**: All SMS permissions required at runtime
4. **Background**: Service runs continuously - impacts battery
5. **Network**: Requires stable internet for API communication

---

## 📚 Additional Documentation

- `SMS_REPLY_HANDLER_SPECIFICATION.md`: Complete backend API specification
- `BACKEND_IMPLEMENTATION_CHECKLIST.md`: Backend deployment checklist
- `INTEGRATION_TEST_GUIDE.md`: Comprehensive testing procedures
- `PRODUCTION_READY_SUMMARY.md`: Production deployment summary
- `README.md`: Basic project overview
- `API_EXAMPLES.json`: Sample API requests/responses (if present)

---

*Last Updated: August 2025*
*Maintained for use with Claude Code (claude.ai/code)*
*Latest Enhancement: Robust background operation for mex.mk deployment*