# SMS Reply Handler Backend Integration Guide
**For MEX SMS Gateway App - Incoming SMS Reply Processing**

## 🔄 Reply Handler Overview

The SMS Gateway app automatically captures ALL incoming SMS messages to the device and sends them to your backend. The app intelligently classifies replies and links them to original shipments when possible.

```
[Recipient Replies] → [App Captures SMS] → [Classifies Reply] → [POST to Backend] → [Backend Processes]
```

---

## 📨 Reply Handler Endpoint

### **Endpoint Configuration**
```http
POST https://mex.mk/api/sms/reply_handler.php
Headers: 
  X-API-Key: osafu2379jsaf
  Content-Type: application/json
```

---

## 📊 Reply Data Structure

### **Complete Reply Payload**
```json
{
  "phone_from": "070345266",
  "phone_to": "+389XXXXXXXX",
  "message": "PIN: 4567",
  "received_timestamp": 1703123456789,
  "shipment_id": 2499370,
  "original_queue_id": 1043,
  "reply_type": "pin_confirmation",
  "device_info": {
    "android_version": "13",
    "app_version": "1.0.0",
    "device_id": "AND_1703123456_device"
  }
}
```

### **Field Descriptions**

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `phone_from` | String | Sender's phone number | "070345266" |
| `phone_to` | String | Device's phone number (if available) | "+389XXXXXXXX" |
| `message` | String | Full SMS text content | "PIN: 4567" |
| `received_timestamp` | Long | Unix timestamp in milliseconds | 1703123456789 |
| `shipment_id` | Long/null | Linked shipment ID (if matched) | 2499370 or null |
| `original_queue_id` | Int/null | Original SMS queue ID (if matched) | 1043 or null |
| `reply_type` | String | Classified reply type | "pin_confirmation" |
| `device_info` | Object | Device metadata | See structure above |

---

## 🏷️ Reply Type Classifications

The app automatically classifies incoming messages into these types:

### **1. PIN Confirmation** (`pin_confirmation`)
**Patterns detected:**
- "PIN: 1234"
- "pin 1234"
- "PIN:1234"
- "Pin code: 1234"
- Case-insensitive matching

**Example messages:**
- "PIN: 4567"
- "My pin is 1234"
- "pin:9876"

### **2. Opt-out Request** (`opt_out`)
**Keywords detected:**
- STOP
- UNSUBSCRIBE
- CANCEL
- REMOVE
- DELETE
- OPTOUT

**Example messages:**
- "STOP"
- "Please unsubscribe me"
- "Cancel all messages"

### **3. Delivery Confirmation** (`delivery_confirmation`)
**Keywords detected:**
- DELIVERED
- RECEIVED
- PRIMIO/PRIMILA (Serbian)
- GOT IT
- DOBIO/DOBILA (Serbian)

**Example messages:**
- "Delivered"
- "Package received, thanks"
- "Primio sam paket"

### **4. Reschedule Request** (`reschedule`)
**Keywords detected:**
- POSTPONE
- DELAY
- RESCHEDULE
- TOMORROW
- LATER
- SUTRA (Serbian)

**Example messages:**
- "Please delay delivery"
- "Can you come tomorrow?"
- "Postpone to next week"

### **5. Complaint** (`complaint`)
**Keywords detected:**
- PROBLEM
- ISSUE
- COMPLAINT
- WRONG
- ERROR
- MISTAKE
- DAMAGED

**Example messages:**
- "There's a problem with my order"
- "Wrong item delivered"
- "Package is damaged"

### **6. General Inquiry** (`inquiry`)
**Detected when:**
- Message contains "?" (question mark)
- Message has question keywords (what, when, where, how, why)

**Example messages:**
- "When will it arrive?"
- "What time?"
- "Is this correct?"

### **7. Unknown** (`unknown`)
**When no pattern matches**

**Example messages:**
- "OK"
- "Thanks"
- Random text

---

## 🔗 Shipment Linking Logic

The app attempts to link replies to original shipments using:

1. **Phone number matching** - Links replies from numbers we recently sent SMS to
2. **Time window** - Considers messages sent in last 24-48 hours
3. **Last shipment** - Links to most recent shipment for that phone number

### **When shipment_id is null**
- Reply is from an unknown number
- No SMS was sent to this number recently
- Manual review may be required

### **When shipment_id is present**
- Reply is linked to a specific shipment
- Can be automatically processed
- High confidence in correlation

---

## 📝 Backend Implementation

### **PHP Implementation Example**

```php
<?php
// /api/sms/reply_handler.php

header('Content-Type: application/json');

// Verify API key
$api_key = $_SERVER['HTTP_X_API_KEY'] ?? '';
if ($api_key !== 'osafu2379jsaf') {
    http_response_code(401);
    exit(json_encode(['error' => 'Unauthorized']));
}

// Get POST data
$input = json_decode(file_get_contents('php://input'), true);

if (!$input) {
    http_response_code(400);
    exit(json_encode(['error' => 'Invalid JSON']));
}

// Extract reply data
$phone_from = $input['phone_from'] ?? '';
$phone_to = $input['phone_to'] ?? '';
$message = $input['message'] ?? '';
$received_timestamp = $input['received_timestamp'] ?? null;
$shipment_id = $input['shipment_id'] ?? null;
$original_queue_id = $input['original_queue_id'] ?? null;
$reply_type = $input['reply_type'] ?? 'unknown';
$device_info = $input['device_info'] ?? [];

// Validate required fields
if (empty($phone_from) || empty($message)) {
    http_response_code(400);
    exit(json_encode(['error' => 'Missing required fields']));
}

// Store reply in database
$sql = "INSERT INTO sms_replies (
    phone_from,
    phone_to,
    message,
    received_timestamp,
    shipment_id,
    original_queue_id,
    reply_type,
    device_android_version,
    device_app_version,
    device_id,
    created_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

$stmt = $pdo->prepare($sql);
$stmt->execute([
    $phone_from,
    $phone_to,
    $message,
    $received_timestamp,
    $shipment_id,
    $original_queue_id,
    $reply_type,
    $device_info['android_version'] ?? null,
    $device_info['app_version'] ?? null,
    $device_info['device_id'] ?? null
]);

$reply_id = $pdo->lastInsertId();

// Process based on reply type
switch ($reply_type) {
    case 'pin_confirmation':
        processPinConfirmation($shipment_id, $message);
        break;
        
    case 'opt_out':
        processOptOut($phone_from);
        break;
        
    case 'delivery_confirmation':
        processDeliveryConfirmation($shipment_id);
        break;
        
    case 'reschedule':
        processRescheduleRequest($shipment_id, $message);
        break;
        
    case 'complaint':
        processComplaint($shipment_id, $message, $phone_from);
        break;
        
    case 'inquiry':
        processInquiry($shipment_id, $message, $phone_from);
        break;
        
    default:
        // Log unknown replies for manual review
        logUnknownReply($reply_id);
}

// Send success response
echo json_encode([
    'success' => true,
    'reply_id' => $reply_id,
    'message' => 'Reply processed successfully'
]);

// Helper functions
function processPinConfirmation($shipment_id, $message) {
    // Extract PIN from message
    preg_match('/pin[:\s]*(\d{4,6})/i', $message, $matches);
    $pin = $matches[1] ?? '';
    
    if ($pin && $shipment_id) {
        // Update shipment with confirmed PIN
        global $pdo;
        $sql = "UPDATE shipments SET 
                pin_confirmed = ?, 
                pin_confirmed_at = NOW() 
                WHERE shipment_id = ?";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([$pin, $shipment_id]);
        
        // Log successful PIN confirmation
        error_log("PIN confirmed for shipment $shipment_id: $pin");
    }
}

function processOptOut($phone) {
    global $pdo;
    // Add to opt-out list
    $sql = "INSERT INTO opt_out_list (phone, opted_out_at) 
            VALUES (?, NOW()) 
            ON DUPLICATE KEY UPDATE opted_out_at = NOW()";
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$phone]);
    
    error_log("Phone $phone added to opt-out list");
}

function processDeliveryConfirmation($shipment_id) {
    if ($shipment_id) {
        global $pdo;
        $sql = "UPDATE shipments SET 
                delivery_confirmed = 1, 
                delivery_confirmed_at = NOW() 
                WHERE shipment_id = ?";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([$shipment_id]);
        
        error_log("Delivery confirmed for shipment $shipment_id");
    }
}

function processRescheduleRequest($shipment_id, $message) {
    if ($shipment_id) {
        // Create a task for customer service
        global $pdo;
        $sql = "INSERT INTO reschedule_requests 
                (shipment_id, request_message, created_at) 
                VALUES (?, ?, NOW())";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([$shipment_id, $message]);
        
        error_log("Reschedule requested for shipment $shipment_id");
    }
}

function processComplaint($shipment_id, $message, $phone) {
    // Create high-priority ticket
    global $pdo;
    $sql = "INSERT INTO complaints 
            (shipment_id, phone, message, priority, created_at) 
            VALUES (?, ?, ?, 'HIGH', NOW())";
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$shipment_id, $phone, $message]);
    
    error_log("Complaint received for shipment $shipment_id from $phone");
}

function processInquiry($shipment_id, $message, $phone) {
    // Create customer service ticket
    global $pdo;
    $sql = "INSERT INTO inquiries 
            (shipment_id, phone, message, created_at) 
            VALUES (?, ?, ?, NOW())";
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$shipment_id, $phone, $message]);
    
    error_log("Inquiry received from $phone about shipment $shipment_id");
}

function logUnknownReply($reply_id) {
    error_log("Unknown reply type for reply ID: $reply_id - Manual review required");
}
?>
```

---

## 🗄️ Database Schema

### **SMS Replies Table**
```sql
CREATE TABLE sms_replies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    phone_from VARCHAR(20) NOT NULL,
    phone_to VARCHAR(20),
    message TEXT NOT NULL,
    received_timestamp BIGINT,
    shipment_id VARCHAR(255),
    original_queue_id INT,
    reply_type VARCHAR(50),
    device_android_version VARCHAR(10),
    device_app_version VARCHAR(10),
    device_id VARCHAR(100),
    processed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_phone_from (phone_from),
    INDEX idx_shipment_id (shipment_id),
    INDEX idx_reply_type (reply_type),
    INDEX idx_received (received_timestamp)
);
```

### **Supporting Tables**
```sql
-- PIN confirmations
CREATE TABLE pin_confirmations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    shipment_id VARCHAR(255) NOT NULL,
    pin_code VARCHAR(10) NOT NULL,
    confirmed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_shipment (shipment_id)
);

-- Opt-out list
CREATE TABLE opt_out_list (
    phone VARCHAR(20) PRIMARY KEY,
    opted_out_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    opted_in_at TIMESTAMP NULL
);

-- Complaints tracking
CREATE TABLE complaints (
    id INT AUTO_INCREMENT PRIMARY KEY,
    shipment_id VARCHAR(255),
    phone VARCHAR(20) NOT NULL,
    message TEXT,
    priority ENUM('LOW', 'MEDIUM', 'HIGH') DEFAULT 'MEDIUM',
    resolved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_priority_unresolved (priority, resolved)
);
```

---

## 🧪 Testing Your Implementation

### **Test PIN Confirmation**
Send an SMS reply: "PIN: 1234"
```bash
curl -X POST "https://mex.mk/api/sms/reply_handler.php" \
  -H "X-API-Key: osafu2379jsaf" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_from": "070345266",
    "message": "PIN: 1234",
    "reply_type": "pin_confirmation",
    "shipment_id": 2499370,
    "received_timestamp": 1703123456789
  }'
```

### **Test Opt-Out**
Send an SMS reply: "STOP"
```bash
curl -X POST "https://mex.mk/api/sms/reply_handler.php" \
  -H "X-API-Key: osafu2379jsaf" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_from": "070345266",
    "message": "STOP",
    "reply_type": "opt_out",
    "received_timestamp": 1703123456789
  }'
```

---

## 📊 Analytics Queries

### **Daily Reply Statistics**
```sql
SELECT 
    DATE(created_at) as date,
    reply_type,
    COUNT(*) as count
FROM sms_replies
GROUP BY DATE(created_at), reply_type
ORDER BY date DESC, count DESC;
```

### **PIN Confirmation Rate**
```sql
SELECT 
    COUNT(DISTINCT sr.shipment_id) as total_shipments,
    COUNT(DISTINCT pc.shipment_id) as confirmed_pins,
    ROUND(COUNT(DISTINCT pc.shipment_id) * 100.0 / COUNT(DISTINCT sr.shipment_id), 2) as confirmation_rate
FROM sms_queue sq
LEFT JOIN sms_replies sr ON sq.shipment_id = sr.shipment_id
LEFT JOIN pin_confirmations pc ON sq.shipment_id = pc.shipment_id
WHERE sq.created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY);
```

### **Unlinked Replies (Requiring Manual Review)**
```sql
SELECT 
    phone_from,
    message,
    reply_type,
    received_timestamp
FROM sms_replies
WHERE shipment_id IS NULL
  AND created_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)
ORDER BY received_timestamp DESC;
```

---

## ⚡ Real-time Processing Recommendations

### **Immediate Actions for Each Reply Type**

| Reply Type | Immediate Action | Priority |
|------------|------------------|----------|
| `pin_confirmation` | Update shipment status, notify driver | HIGH |
| `opt_out` | Add to blacklist, prevent future SMS | HIGH |
| `delivery_confirmation` | Mark as delivered, close shipment | MEDIUM |
| `reschedule` | Create CS ticket, notify dispatcher | HIGH |
| `complaint` | Alert supervisor, create urgent ticket | URGENT |
| `inquiry` | Create CS ticket for response | MEDIUM |
| `unknown` | Queue for manual review | LOW |

---

## 🔐 Security Considerations

1. **Validate API Key** - Always check the X-API-Key header
2. **Sanitize Input** - Clean message content before storage
3. **Rate Limiting** - Implement rate limits to prevent abuse
4. **PII Protection** - Be careful with phone numbers and messages
5. **Audit Logging** - Log all reply processing for compliance

---

## 🎯 Best Practices

### **DO:**
- ✅ Process PIN confirmations immediately
- ✅ Respect opt-out requests instantly
- ✅ Link replies to shipments when possible
- ✅ Log all unmatched replies for review
- ✅ Send success response even if processing fails

### **DON'T:**
- ❌ Reject replies without shipment_id
- ❌ Ignore unknown reply types
- ❌ Send SMS to opted-out numbers
- ❌ Expose internal errors in responses
- ❌ Process same reply twice

---

## 📈 Monitoring Dashboard Queries

### **Reply Processing Health**
```sql
-- Last hour reply statistics
SELECT 
    reply_type,
    COUNT(*) as count,
    COUNT(CASE WHEN shipment_id IS NOT NULL THEN 1 END) as linked,
    COUNT(CASE WHEN shipment_id IS NULL THEN 1 END) as unlinked
FROM sms_replies
WHERE received_timestamp >= UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 1 HOUR)) * 1000
GROUP BY reply_type;
```

### **Response Time Analysis**
```sql
-- Average time between SMS sent and PIN received
SELECT 
    AVG((sr.received_timestamp - sq.sent_timestamp) / 1000) as avg_response_seconds,
    MIN((sr.received_timestamp - sq.sent_timestamp) / 1000) as min_response_seconds,
    MAX((sr.received_timestamp - sq.sent_timestamp) / 1000) as max_response_seconds
FROM sms_replies sr
JOIN sms_queue sq ON sr.original_queue_id = sq.queue_id
WHERE sr.reply_type = 'pin_confirmation'
  AND sr.received_timestamp > sq.sent_timestamp;
```

---

## ✅ Implementation Checklist

- [ ] Create `/api/sms/reply_handler.php` endpoint
- [ ] Implement API key validation
- [ ] Parse and validate JSON payload
- [ ] Create sms_replies table
- [ ] Implement reply type processing logic
- [ ] Handle PIN extraction and validation
- [ ] Implement opt-out list management
- [ ] Create complaint escalation process
- [ ] Set up monitoring queries
- [ ] Test with sample payloads
- [ ] Implement error logging
- [ ] Add rate limiting
- [ ] Create manual review queue for unknown types
- [ ] Set up alerts for urgent reply types

---

## 🚨 Common Issues & Solutions

### **Issue: Shipment ID is always null**
**Solution:** This happens when the recipient hasn't received an SMS from you recently. Store and process these replies separately for manual review.

### **Issue: Multiple PIN messages for same shipment**
**Solution:** Only process the first PIN confirmation. Update your logic to check if PIN was already confirmed.

### **Issue: False positive classifications**
**Solution:** The app's classification is a suggestion. Implement your own validation logic based on your business rules.

### **Issue: High volume of unknown replies**
**Solution:** Analyze patterns in unknown replies and request app update for new classification rules.

---

## 📞 Support

For questions about reply handling:
1. Check the app logs for classification details
2. Monitor the reply_type distribution
3. Review unlinked replies regularly
4. Adjust classification logic as needed

**Remember:** The app captures ALL incoming SMS, not just replies to your messages. Implement filtering based on your business logic.

---

*Last Updated: September 2025*
*SMS Gateway App Version: 1.0.0*
*Reply Handler Endpoint: https://mex.mk/api/sms/reply_handler.php*