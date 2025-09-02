# SMS Reply Handler - Backend Implementation Specification

## Executive Summary
This document outlines the backend implementation requirements for handling SMS replies from recipients back to the system. The solution uses a **separate dedicated endpoint** to maintain clean separation of concerns and allow for independent scaling and monitoring of reply handling.

---

## Table of Contents
1. [Architecture Decision](#architecture-decision)
2. [API Endpoint Specification](#api-endpoint-specification)
3. [Data Models](#data-models)
4. [Implementation Requirements](#implementation-requirements)
5. [Database Schema](#database-schema)
6. [Security Considerations](#security-considerations)
7. [Android App Integration](#android-app-integration)
8. [Testing Strategy](#testing-strategy)
9. [Monitoring & Analytics](#monitoring--analytics)

---

## Architecture Decision

### Recommendation: **Separate Endpoint**

**Endpoint**: `/api/sms/reply_handler.php`

### Rationale:
1. **Separation of Concerns**: Keeps reply handling logic separate from the probing/sending logic
2. **Independent Scaling**: Reply volume may differ from send volume
3. **Cleaner Monitoring**: Easier to track reply metrics separately
4. **Maintenance**: Simpler to maintain and debug isolated functionality
5. **Rate Limiting**: Can apply different rate limits to replies vs sends
6. **Permissions**: May require different access controls

---

## API Endpoint Specification

### POST /api/sms/reply_handler.php

**Purpose**: Receive and store SMS replies from recipients

### Request Headers
```http
POST /api/sms/reply_handler.php
Headers:
  X-API-Key: osafu2379jsaf
  Content-Type: application/json
```

### Request Body (Single Reply)
```json
{
  "phone_from": "+381641234567",
  "phone_to": "+381600000000",
  "message": "PIN: 4567 confirmed",
  "received_timestamp": 1703123456000,
  "shipment_id": 12345,
  "original_queue_id": 789,
  "reply_type": "pin_confirmation",
  "device_info": {
    "android_version": "13",
    "app_version": "1.0.0",
    "device_id": "unique_device_identifier"
  }
}
```

### Request Body (Batch Replies)
```json
[
  {
    "phone_from": "+381641234567",
    "phone_to": "+381600000000",
    "message": "PIN: 4567 confirmed",
    "received_timestamp": 1703123456000,
    "shipment_id": 12345,
    "original_queue_id": 789,
    "reply_type": "pin_confirmation"
  },
  {
    "phone_from": "+381641234568",
    "phone_to": "+381600000000",
    "message": "STOP",
    "received_timestamp": 1703123457000,
    "reply_type": "opt_out"
  }
]
```

### Response (Success)
```json
{
  "status": "success",
  "message": "Reply processed successfully",
  "reply_id": 456,
  "processed_count": 1,
  "actions_triggered": [
    {
      "type": "pin_verified",
      "shipment_id": 12345,
      "status": "success"
    }
  ]
}
```

### Response (Batch Success)
```json
{
  "status": "success",
  "message": "Processed 2 replies successfully",
  "results": [
    {
      "reply_id": 456,
      "status": "success",
      "actions_triggered": ["pin_verified"]
    },
    {
      "reply_id": 457,
      "status": "success",
      "actions_triggered": ["opt_out_recorded"]
    }
  ],
  "total_processed": 2,
  "success_count": 2,
  "error_count": 0
}
```

---

## Data Models

### Reply Types Classification
```php
const REPLY_TYPES = [
    'pin_confirmation' => 'PIN code confirmation from recipient',
    'delivery_confirmation' => 'Manual delivery confirmation',
    'reschedule_request' => 'Request to reschedule delivery',
    'opt_out' => 'Unsubscribe request',
    'general_inquiry' => 'General question or comment',
    'complaint' => 'Issue or complaint',
    'unknown' => 'Unclassified reply'
];
```

### Reply Processing Rules
```php
// Automatic pattern matching for reply classification
$patterns = [
    '/\bPIN:?\s*(\d{4})\b/i' => 'pin_confirmation',
    '/\b(STOP|UNSUBSCRIBE|CANCEL)\b/i' => 'opt_out',
    '/\b(DELIVERED|RECEIVED|GOT IT)\b/i' => 'delivery_confirmation',
    '/\b(RESCHEDULE|POSTPONE|DELAY)\b/i' => 'reschedule_request',
    '/\b(PROBLEM|ISSUE|WRONG|ERROR)\b/i' => 'complaint'
];
```

---

## Implementation Requirements

### 1. Core Functionality
```php
class SmsReplyHandler {
    
    private $db;
    private $validator;
    private $classifier;
    private $actionProcessor;
    
    public function processReply($replyData) {
        // 1. Validate incoming data
        $validated = $this->validator->validate($replyData);
        
        // 2. Classify reply type
        $replyType = $this->classifier->classify($validated['message']);
        
        // 3. Store reply in database
        $replyId = $this->storeReply($validated, $replyType);
        
        // 4. Trigger automated actions based on type
        $actions = $this->actionProcessor->process($replyType, $validated);
        
        // 5. Update related shipment records
        $this->updateShipmentStatus($validated, $actions);
        
        // 6. Send notifications if needed
        $this->notifyRelevantParties($validated, $actions);
        
        return [
            'reply_id' => $replyId,
            'actions_triggered' => $actions
        ];
    }
    
    private function storeReply($data, $type) {
        $sql = "INSERT INTO sms_replies (
            phone_from, phone_to, message, reply_type,
            received_timestamp, shipment_id, original_queue_id,
            device_info, created_at, processed_status
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'processed')";
        
        // Execute and return insert ID
    }
    
    private function classifyReply($message) {
        // Pattern matching logic
        // Machine learning classification (future enhancement)
        // Return reply_type
    }
    
    private function processActions($type, $data) {
        switch($type) {
            case 'pin_confirmation':
                return $this->verifyPinCode($data);
            
            case 'opt_out':
                return $this->processOptOut($data);
            
            case 'reschedule_request':
                return $this->createRescheduleTicket($data);
            
            default:
                return $this->logForManualReview($data);
        }
    }
}
```

### 2. PIN Verification Logic
```php
private function verifyPinCode($data) {
    // Extract PIN from message
    preg_match('/\b(\d{4})\b/', $data['message'], $matches);
    $providedPin = $matches[1] ?? null;
    
    if (!$providedPin) {
        return ['status' => 'failed', 'reason' => 'no_pin_found'];
    }
    
    // Get stored PIN for shipment
    $storedPin = $this->getShipmentPin($data['shipment_id']);
    
    if ($providedPin === $storedPin) {
        // Update shipment status
        $this->updateShipmentStatus($data['shipment_id'], 'pin_verified');
        
        // Log verification
        $this->logPinVerification($data['shipment_id'], $providedPin, true);
        
        return ['status' => 'success', 'action' => 'pin_verified'];
    } else {
        // Log failed attempt
        $this->logPinVerification($data['shipment_id'], $providedPin, false);
        
        // Check for multiple failures
        $failures = $this->countPinFailures($data['shipment_id']);
        if ($failures >= 3) {
            $this->flagShipmentForReview($data['shipment_id']);
        }
        
        return ['status' => 'failed', 'reason' => 'incorrect_pin'];
    }
}
```

### 3. Opt-Out Processing
```php
private function processOptOut($data) {
    // Add to opt-out list
    $this->db->insert('sms_opt_outs', [
        'phone_number' => $data['phone_from'],
        'opt_out_date' => date('Y-m-d H:i:s'),
        'message' => $data['message'],
        'source' => 'sms_reply'
    ]);
    
    // Update customer preferences
    $this->updateCustomerPreferences($data['phone_from'], [
        'sms_enabled' => false,
        'opt_out_reason' => 'customer_request'
    ]);
    
    // Send confirmation (if legally required)
    $this->queueOptOutConfirmation($data['phone_from']);
    
    return ['status' => 'success', 'action' => 'opt_out_recorded'];
}
```

---

## Database Schema

### Table: sms_replies
```sql
CREATE TABLE sms_replies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    phone_from VARCHAR(20) NOT NULL,
    phone_to VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    reply_type VARCHAR(50) DEFAULT 'unknown',
    received_timestamp BIGINT,
    shipment_id BIGINT,
    original_queue_id INT,
    device_info JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_status ENUM('pending', 'processed', 'failed', 'manual_review') DEFAULT 'pending',
    processed_at TIMESTAMP NULL,
    actions_taken JSON,
    notes TEXT,
    INDEX idx_phone_from (phone_from),
    INDEX idx_shipment_id (shipment_id),
    INDEX idx_reply_type (reply_type),
    INDEX idx_received_timestamp (received_timestamp),
    INDEX idx_processed_status (processed_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Table: sms_reply_actions
```sql
CREATE TABLE sms_reply_actions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reply_id INT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_status ENUM('success', 'failed', 'pending') DEFAULT 'pending',
    action_data JSON,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT,
    FOREIGN KEY (reply_id) REFERENCES sms_replies(id),
    INDEX idx_action_type (action_type),
    INDEX idx_action_status (action_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Table: sms_opt_outs
```sql
CREATE TABLE sms_opt_outs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    opt_out_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    opt_in_date TIMESTAMP NULL,
    status ENUM('active', 'inactive') DEFAULT 'active',
    source VARCHAR(50),
    message TEXT,
    INDEX idx_phone_number (phone_number),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Security Considerations

### 1. Authentication & Authorization
- Require API key for all requests
- Implement rate limiting (max 100 replies/minute per device)
- Validate device_id consistency
- Log all API access attempts

### 2. Data Validation
```php
$validationRules = [
    'phone_from' => 'required|phone_number',
    'phone_to' => 'required|phone_number',
    'message' => 'required|string|max:1000',
    'received_timestamp' => 'required|integer',
    'shipment_id' => 'integer|exists:shipments,id',
    'original_queue_id' => 'integer|exists:sms_queue,id'
];
```

### 3. Privacy Protection
- Mask sensitive data in logs
- Encrypt stored messages
- Implement data retention policies (delete after 90 days)
- GDPR compliance for EU numbers

### 4. Abuse Prevention
- Detect spam patterns
- Block repeated invalid PIN attempts
- Monitor for unusual reply volumes
- Implement CAPTCHA for suspicious patterns

---

## Android App Integration

### Required Changes to Android App

#### 1. SMS Receiver Implementation
```java
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                String sender = smsMessage.getDisplayOriginatingAddress();
                String message = smsMessage.getMessageBody();
                long timestamp = smsMessage.getTimestampMillis();
                
                // Check if this is a reply to our SMS
                if (isReplyToOurSms(sender)) {
                    processReply(sender, message, timestamp);
                }
            }
        }
    }
    
    private void processReply(String sender, String message, long timestamp) {
        // Find related shipment
        ShipmentInfo shipment = findRelatedShipment(sender);
        
        // Create reply object
        JSONObject reply = new JSONObject();
        reply.put("phone_from", sender);
        reply.put("phone_to", getOurPhoneNumber());
        reply.put("message", message);
        reply.put("received_timestamp", timestamp);
        
        if (shipment != null) {
            reply.put("shipment_id", shipment.id);
            reply.put("original_queue_id", shipment.queueId);
        }
        
        // Classify reply type
        reply.put("reply_type", classifyReply(message));
        
        // Add device info
        JSONObject deviceInfo = new JSONObject();
        deviceInfo.put("android_version", Build.VERSION.RELEASE);
        deviceInfo.put("app_version", BuildConfig.VERSION_NAME);
        deviceInfo.put("device_id", getDeviceId());
        reply.put("device_info", deviceInfo);
        
        // Queue for sending to server
        ReplyQueueManager.getInstance().queueReply(reply);
    }
}
```

#### 2. Manifest Permissions
```xml
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />

<receiver android:name=".receivers.SmsReceiver"
    android:permission="android.permission.BROADCAST_SMS">
    <intent-filter>
        <action android:name="android.provider.Telephony.SMS_RECEIVED" />
    </intent-filter>
</receiver>
```

---

## Testing Strategy

### Test Cases

#### 1. PIN Confirmation Tests
```bash
# Valid PIN
curl -X POST "https://srb.trackify.net/api/sms/reply_handler.php" \
  -H "X-API-Key: osafu2379jsaf" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_from": "+381641234567",
    "message": "PIN: 4567",
    "shipment_id": 12345
  }'

# Invalid PIN
curl -X POST "https://srb.trackify.net/api/sms/reply_handler.php" \
  -H "X-API-Key: osafu2379jsaf" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_from": "+381641234567",
    "message": "PIN: 9999",
    "shipment_id": 12345
  }'
```

#### 2. Opt-Out Tests
```bash
curl -X POST "https://srb.trackify.net/api/sms/reply_handler.php" \
  -H "X-API-Key: osafu2379jsaf" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_from": "+381641234567",
    "message": "STOP"
  }'
```

#### 3. Batch Reply Tests
```bash
curl -X POST "https://srb.trackify.net/api/sms/reply_handler.php" \
  -H "X-API-Key: osafu2379jsaf" \
  -H "Content-Type: application/json" \
  -d '[
    {"phone_from": "+381641234567", "message": "PIN: 4567", "shipment_id": 12345},
    {"phone_from": "+381641234568", "message": "STOP"},
    {"phone_from": "+381641234569", "message": "When will my package arrive?"}
  ]'
```

---

## Monitoring & Analytics

### Key Metrics to Track

1. **Reply Volume Metrics**
   - Total replies per hour/day
   - Reply rate (replies/sent SMS)
   - Peak reply times

2. **Reply Type Distribution**
   - PIN confirmations: X%
   - Opt-outs: Y%
   - Inquiries: Z%

3. **Processing Metrics**
   - Average processing time
   - Success/failure rates
   - Manual review queue size

4. **Business Metrics**
   - PIN verification success rate
   - Opt-out rate trends
   - Customer satisfaction indicators

### Monitoring Queries
```sql
-- Daily reply statistics
SELECT 
    DATE(created_at) as date,
    reply_type,
    COUNT(*) as count,
    AVG(TIMESTAMPDIFF(SECOND, created_at, processed_at)) as avg_process_time
FROM sms_replies
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY DATE(created_at), reply_type;

-- PIN verification success rate
SELECT 
    DATE(created_at) as date,
    SUM(CASE WHEN actions_taken LIKE '%pin_verified%' THEN 1 ELSE 0 END) as successful,
    COUNT(*) as total,
    (SUM(CASE WHEN actions_taken LIKE '%pin_verified%' THEN 1 ELSE 0 END) / COUNT(*)) * 100 as success_rate
FROM sms_replies
WHERE reply_type = 'pin_confirmation'
GROUP BY DATE(created_at);
```

---

## Implementation Timeline

### Phase 1: Core Implementation (Week 1)
- [ ] Create database tables
- [ ] Implement basic reply endpoint
- [ ] Add authentication and validation
- [ ] Store replies in database

### Phase 2: Classification & Actions (Week 2)
- [ ] Implement reply classification
- [ ] Add PIN verification logic
- [ ] Add opt-out processing
- [ ] Implement action triggers

### Phase 3: Integration (Week 3)
- [ ] Update Android app
- [ ] Test end-to-end flow
- [ ] Add monitoring
- [ ] Documentation

### Phase 4: Optimization (Week 4)
- [ ] Performance tuning
- [ ] Add batch processing
- [ ] Implement caching
- [ ] Load testing

---

## API Response Codes

| Code | Status | Description |
|------|--------|-------------|
| 200 | Success | Reply processed successfully |
| 207 | Multi-Status | Batch processing with mixed results |
| 400 | Bad Request | Invalid request format |
| 401 | Unauthorized | Invalid API key |
| 409 | Conflict | Duplicate reply detected |
| 422 | Unprocessable Entity | Valid format but invalid data |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Server Error | Internal processing error |

---

## Contact Information

**Frontend Integration**: [Your Contact]  
**Backend Development**: [Backend Dev Contact]  
**Project Manager**: [PM Contact]  

---

## Appendix: Sample Implementation Files

### A. reply_handler.php
```php
<?php
// Main endpoint file
require_once 'includes/ReplyHandler.class.php';
require_once 'includes/Authentication.php';
require_once 'includes/Database.php';

// CORS headers
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST');
header('Access-Control-Allow-Headers: X-API-Key, Content-Type');

// Authentication
if (!authenticate($_SERVER['HTTP_X_API_KEY'] ?? '')) {
    http_response_code(401);
    die(json_encode(['status' => 'error', 'message' => 'Unauthorized']));
}

// Get request data
$input = file_get_contents('php://input');
$data = json_decode($input, true);

// Initialize handler
$handler = new ReplyHandler($db);

// Process single or batch
if (isset($data[0])) {
    // Batch processing
    $results = $handler->processBatch($data);
} else {
    // Single reply
    $results = $handler->processSingle($data);
}

// Return response
echo json_encode($results);
?>
```

### B. WordPress Integration Hook
```php
// Add to WordPress functions.php or custom plugin
add_action('sms_reply_received', 'handle_sms_reply', 10, 1);

function handle_sms_reply($reply_data) {
    // Update post meta
    if (!empty($reply_data['shipment_id'])) {
        $post_id = get_post_id_by_meta('shipment_id', $reply_data['shipment_id']);
        
        if ($post_id) {
            update_post_meta($post_id, 'last_sms_reply', $reply_data['message']);
            update_post_meta($post_id, 'last_reply_timestamp', $reply_data['received_timestamp']);
            
            // Handle PIN verification
            if ($reply_data['reply_type'] === 'pin_confirmation') {
                $verified = verify_delivery_pin($post_id, $reply_data['message']);
                if ($verified) {
                    update_post_meta($post_id, 'delivery_confirmed', true);
                    update_post_meta($post_id, 'delivery_confirmed_at', current_time('mysql'));
                }
            }
        }
    }
}
```

---

*Document Version: 1.0*  
*Created: December 2024*  
*Last Updated: December 2024*