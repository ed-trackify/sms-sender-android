# Backend Verification Guide - Serbia Deployment
## SMS Gateway Android App - Trackify Serbia

**Production URL**: https://srb.trackify.net  
**Date**: Sept 2025  
**App Version**: 1.0.3

---

## ✅ Configuration Checklist

### Current Production Settings:
- ✅ **Base URL**: `https://srb.trackify.net`
- ✅ **API Key**: `osafu2379jsaf`
- ✅ **App Name**: `SMS Sender Srbija`
- ✅ **Probe Interval**: 10 seconds
- ✅ **Reply Interval**: 15 seconds
- ✅ **Serbian Language Support**: Enabled

---

## 📡 Required API Endpoints

### 1. SMS Prober Endpoint
**URL**: `https://srb.trackify.net/api/sms/prober.php`  
**Method**: GET & POST  
**Authentication**: Header `X-API-Key: osafu2379jsaf`

#### GET Request (Fetch SMS Tasks)
**Expected Response Format**:
```json
{
  "phone": "+381601234567",
  "message": "Vaš paket stiže danas. PIN: 4567",
  "queue_id": 789,
  "shipment_id": 12345
}
```

**Response Codes**:
- `200` - SMS task available
- `204` - No SMS tasks in queue
- `401` - Invalid API key
- `429` - Rate limit exceeded

#### POST Request (Status Updates)
**Request Body Example**:
```json
{
  "queue_id": 789,
  "phone": "+381601234567",
  "status": "delivered",
  "sent_timestamp": 1737000000000,
  "delivered_timestamp": 1737000010000,
  "delivery_time_seconds": 10
}
```

**Status Progression**:
1. `processing` - SMS received by app
2. `pending` - SMS queued for sending
3. `sent` - SMS sent successfully
4. `delivered` - Delivery confirmed
5. `failed` - SMS failed to send
6. `sent_unconfirmed` - Sent but no delivery confirmation after 30 seconds

---

### 2. Reply Handler Endpoint
**URL**: `https://srb.trackify.net/api/sms/reply_handler.php`  
**Method**: POST  
**Authentication**: Header `X-API-Key: osafu2379jsaf`

#### Request Body Example:
```json
{
  "phone_from": "+381601234567",
  "phone_to": "+381600000000",
  "message": "PIN: 4567",
  "received_timestamp": 1737000000000,
  "shipment_id": 12345,
  "original_queue_id": 789,
  "reply_type": "pin_confirmation",
  "device_info": {
    "android_version": "13",
    "app_version": "1.0.2",
    "device_id": "AND_1737000000_device"
  }
}
```

#### Reply Types (with Serbian Keywords):
| Type | Keywords (English) | Keywords (Serbian) | Example |
|------|-------------------|-------------------|---------|
| `pin_confirmation` | PIN: XXXX | PIN: XXXX | "PIN: 4567" |
| `opt_out` | STOP, UNSUBSCRIBE | ODJAVI, PREKINI, OTKAŽI | "ODJAVI me" |
| `delivery_confirmation` | DELIVERED, RECEIVED | DOSTAVLJENO, PRIMLJENO, PREUZETO | "Paket PRIMLJENO" |
| `reschedule_request` | POSTPONE, DELAY | ODLOŽI, KASNIJE, SUTRA, POMERI | "Molim ODLOŽI za sutra" |
| `complaint` | PROBLEM, ISSUE | GREŠKA, POGREŠNO, ŽALBA | "Imam PROBLEM sa isporukom" |
| `general_inquiry` | Questions with ? | KADA, GDE, KAKO, ŠTA, ZAŠTO | "KADA stiže paket?" |
| `unknown` | - | - | Any other message |

---

## 🔐 Security Requirements

### API Authentication:
All requests MUST include the header:
```
X-API-Key: osafu2379jsaf
```

### Expected Error Responses:
```json
// 401 Unauthorized
{
  "error": "Invalid API key",
  "code": 401
}

// 429 Too Many Requests
{
  "error": "Rate limit exceeded",
  "code": 429,
  "retry_after": 60
}

// 500 Server Error
{
  "error": "Internal server error",
  "code": 500
}
```

---

## 📊 Database Schema Requirements

### Required Tables:

#### 1. SMS Queue Table
```sql
CREATE TABLE sms_queue (
    queue_id INT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    shipment_id BIGINT,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    delivery_time_seconds INT NULL,
    INDEX idx_status (status),
    INDEX idx_shipment (shipment_id)
);
```

#### 2. SMS Replies Table
```sql
CREATE TABLE sms_replies (
    reply_id INT PRIMARY KEY AUTO_INCREMENT,
    phone_from VARCHAR(20) NOT NULL,
    phone_to VARCHAR(20),
    message TEXT NOT NULL,
    received_timestamp BIGINT,
    shipment_id BIGINT,
    original_queue_id INT,
    reply_type VARCHAR(30),
    device_id VARCHAR(100),
    android_version VARCHAR(10),
    app_version VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_shipment (shipment_id),
    INDEX idx_phone (phone_from),
    INDEX idx_type (reply_type)
);
```

---

## 🧪 Testing Checklist

### Basic Functionality Tests:

#### 1. SMS Prober Test
```bash
# Test GET request
curl -H "X-API-Key: osafu2379jsaf" \
     https://srb.trackify.net/api/sms/prober.php

# Expected: Either SMS task JSON or 204 No Content
```

#### 2. Status Update Test
```bash
# Test POST status update
curl -X POST \
     -H "X-API-Key: osafu2379jsaf" \
     -H "Content-Type: application/json" \
     -d '{"queue_id":1,"status":"delivered","phone":"+381601234567"}' \
     https://srb.trackify.net/api/sms/prober.php

# Expected: 200 OK
```

#### 3. Reply Handler Test
```bash
# Test reply submission
curl -X POST \
     -H "X-API-Key: osafu2379jsaf" \
     -H "Content-Type: application/json" \
     -d '{"phone_from":"+381601234567","message":"PIN: 1234","reply_type":"pin_confirmation"}' \
     https://srb.trackify.net/api/sms/reply_handler.php

# Expected: 200 OK
```

### Serbian Language Tests:
- ✅ Send SMS with Serbian text: "Vaš paket stiže danas"
- ✅ Process Serbian opt-out: "ODJAVI me molim"
- ✅ Handle Serbian delivery confirmation: "PRIMLJENO"
- ✅ Parse Serbian questions: "KADA stiže moja porudžbina?"

---

## 📱 App Behavior Summary

### Polling Intervals:
- **SMS Tasks**: Every 10 seconds
- **Reply Queue**: Every 15 seconds (or when 10 replies accumulated)

### Background Operation:
- Runs 24/7 as foreground service
- Auto-restarts on device boot
- Maintains wake lock for continuous operation
- Shows persistent notification: "Trackify SMS Bot"

### SMS Processing Flow:
```
1. App polls /api/sms/prober.php every 10 seconds
2. If task received → Send SMS
3. Track delivery status (30-second timeout)
4. Update status via POST to /api/sms/prober.php
5. Monitor incoming SMS for replies
6. Classify and queue replies
7. Batch send replies to /api/sms/reply_handler.php
```

---

## 🚨 Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| No SMS tasks received | Check API key, verify endpoint URL |
| Status updates failing | Ensure POST accepts JSON, check queue_id exists |
| Replies not received | Verify reply_handler.php endpoint active |
| Serbian text garbled | Ensure UTF-8 encoding throughout |
| High server load | Increase probe interval (currently 10s) |

---

## 📞 Support Information

### Current Configuration:
- **Production URL**: https://srb.trackify.net
- **API Version**: 1.0
- **App Version**: 1.0.2
- **Target Country**: Serbia (+381)
- **Language**: Serbian (Cyrillic & Latin)

### Build Instructions:
```bash
# To rebuild APK with changes:
export JAVA_HOME="$(pwd)/jdk-17"
./gradlew clean assembleDebug    # Debug version
./gradlew clean assembleRelease  # Production version

# APK location: app/build/outputs/apk/
```

---

## ✅ Final Verification Steps

Before going live, ensure:

1. [ ] Both API endpoints respond with correct status codes
2. [ ] API key authentication is working
3. [ ] Database tables are created with proper indexes
4. [ ] Serbian text encoding (UTF-8) works correctly
5. [ ] SMS queue has test messages ready
6. [ ] Reply handler logs incoming messages
7. [ ] Monitor dashboard shows real-time statistics
8. [ ] Error logging is enabled for debugging
9. [ ] Rate limiting is configured if needed
10. [ ] SSL certificate is valid for srb.trackify.net

---

**Last Updated**: January 2025  
**Deployment Target**: Serbia (Srbija)  
**Primary Language**: Serbian (Српски)