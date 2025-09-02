# SMS Gateway Backend Integration Guide
**For MEX SMS Gateway App - Backend Synchronization**

## 🔄 Complete Status Flow Overview

The SMS Gateway app sends **FIVE different status updates** for each SMS message. Your backend should handle all of these statuses to properly track SMS delivery.

```
[Backend] ─GET─> [App] ─Process─> [Send SMS] ─POST Status Updates─> [Backend]
```

---

## 📊 All Status Types Sent by the App

### **Status Progression for Successful SMS**
1. **`processing`** → SMS task received from server, starting to process
2. **`pending`** → About to send SMS via Android SMS API
3. **`sent`** → SMS successfully left the device (carrier accepted it)
4. **`delivered`** → Carrier confirmed SMS was delivered to recipient *(if supported)*

### **Alternative Status Outcomes**
- **`failed`** → SMS failed to send (replaces `sent` if sending fails)
- **`sent_unconfirmed`** → SMS was sent but delivery status unknown (rare)

---

## 🔁 Detailed Workflow

### **Step 1: App Fetches SMS Task**
```http
GET https://mex.mk/api/sms/prober.php
Headers: X-API-Key: osafu2379jsaf
```

**Your Response:**
```json
{
  "queue_id": 1043,
  "phone": "070345266",
  "message": "Your message here",
  "shipment_id": 2499370
}
```

### **Step 2: App Sends Multiple Status Updates**

The app will POST to the same endpoint multiple times with different statuses:

#### **Update 1: Processing Status**
```json
{
  "queue_id": 1043,
  "phone": "070345266",
  "status": "processing",
  "shipment_id": 2499370,
  "sms_sent": "Your message here"
}
```

#### **Update 2: Pending Status**
```json
{
  "queue_id": 1043,
  "phone": "070345266",
  "status": "pending",
  "shipment_id": 2499370,
  "sms_sent": "Your message here"
}
```

#### **Update 3: Sent Status**
```json
{
  "queue_id": 1043,
  "phone": "070345266",
  "status": "sent",
  "shipment_id": 2499370,
  "sent_timestamp": 1703123456789,
  "sms_sent": "Your message here"
}
```

#### **Update 4: Delivered Status** *(if carrier provides delivery report)*
```json
{
  "queue_id": 1043,
  "phone": "070345266",
  "status": "delivered",
  "shipment_id": 2499370,
  "sent_timestamp": 1703123456789,
  "delivered_timestamp": 1703123458789,
  "delivery_time_seconds": 2,
  "sms_sent": "Your message here"
}
```

---

## ⚠️ Important: Status Updates Can Be Batched

The app may send updates as an **array** (batch) or **single object**:

### **Batch Format (Array)**
```json
[
  {"queue_id": 1043, "status": "processing", "phone": "070345266", ...},
  {"queue_id": 1043, "status": "pending", "phone": "070345266", ...},
  {"queue_id": 1043, "status": "sent", "phone": "070345266", ...}
]
```

### **Single Format (Object)**
```json
{"queue_id": 1043, "status": "delivered", "phone": "070345266", ...}
```

**Your backend MUST handle both formats!**

---

## 🚨 Failure Scenarios

### **When SMS Fails to Send**
```json
{
  "queue_id": 1043,
  "phone": "070345266",
  "status": "failed",
  "error_code": "GENERIC_FAILURE",
  "shipment_id": 2499370,
  "sent_timestamp": 1703123456789,
  "sms_sent": "Your message here"
}
```

### **Possible Error Codes**
- `GENERIC_FAILURE` - General SMS failure
- `NO_SERVICE` - No cellular service
- `NULL_PDU` - Invalid SMS data
- `RADIO_OFF` - Phone radio disabled
- `EXCEPTION: [message]` - App-level error

---

## 📝 Backend Implementation Requirements

### **1. Handle Multiple Updates for Same SMS**
Each SMS will generate 3-4 status updates. Don't treat them as duplicates!

```php
// PHP Example
$status = $input['status'];
$queue_id = $input['queue_id'];

// Update the status - this will be called multiple times
$sql = "UPDATE sms_queue SET status = ?, updated_at = NOW() WHERE queue_id = ?";
$stmt->execute([$status, $queue_id]);

// Log each status change
error_log("SMS Status Change: Queue $queue_id now $status");
```

### **2. Handle Both Array and Object Formats**
```php
// PHP Example
$input = file_get_contents('php://input');
$data = json_decode($input, true);

// Check if it's an array (batch) or single object
if (isset($data[0])) {
    // It's an array - process each update
    foreach ($data as $update) {
        processStatusUpdate($update);
    }
} else {
    // It's a single object
    processStatusUpdate($data);
}
```

### **3. Track Status Progression**
```sql
-- Add a status history table
CREATE TABLE sms_status_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    queue_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(100),
    timestamp BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_queue_id (queue_id)
);
```

---

## 🎯 Status Timeline Example

For a successful SMS with delivery confirmation:

```
Time    Status          Description
------  -------------   --------------------------------------------
0s      processing      App received task from your API
0.1s    pending         App preparing to send SMS
0.5s    sent           SMS left the device successfully
3s      delivered      Carrier confirmed delivery to recipient
```

For a failed SMS:

```
Time    Status          Description
------  -------------   --------------------------------------------
0s      processing      App received task from your API
0.1s    pending         App preparing to send SMS
0.5s    failed         SMS failed to send (with error_code)
```

---

## 🔍 Testing Your Integration

### **1. Test Successful Flow**
Send an SMS to a working number and verify you receive:
- `processing` status
- `pending` status
- `sent` status
- `delivered` status (if carrier supports it)

### **2. Test Failure Flow**
Send an SMS to an invalid number (like "123") and verify you receive:
- `processing` status
- `pending` status
- `failed` status with error_code

### **3. Test Batch Processing**
The app sends updates in batches every 10 seconds or when critical status occurs.

---

## 📌 Key Points for Backend Developer

1. **EXPECT MULTIPLE UPDATES** - Each SMS generates 3-4 status updates
2. **STATUS ORDER MATTERS** - Track the progression: processing → pending → sent → delivered
3. **DELIVERED IS OPTIONAL** - Not all carriers provide delivery reports
4. **HANDLE BOTH FORMATS** - Updates can be single object or array
5. **USE QUEUE_ID** - This is the unique identifier for each SMS task
6. **TIMESTAMPS IN MILLISECONDS** - Unix timestamps are in milliseconds, not seconds
7. **ERROR CODES ARE IMPORTANT** - Store error_code when status is "failed"

---

## 🛠️ Sample Backend Handler (Complete PHP Example)

```php
<?php
// /api/sms/prober.php

header('Content-Type: application/json');

// Verify API key
$api_key = $_SERVER['HTTP_X_API_KEY'] ?? '';
if ($api_key !== 'osafu2379jsaf') {
    http_response_code(401);
    exit(json_encode(['error' => 'Unauthorized']));
}

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    // Return next SMS to send
    $sms = getNextSmsFromQueue(); // Your function
    if ($sms) {
        echo json_encode([
            'queue_id' => $sms['id'],
            'phone' => $sms['phone'],
            'message' => $sms['message'],
            'shipment_id' => $sms['shipment_id']
        ]);
    } else {
        http_response_code(204); // No content
    }
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // Handle status updates
    $input = json_decode(file_get_contents('php://input'), true);
    
    // Check if batch or single
    $updates = isset($input[0]) ? $input : [$input];
    
    $processed = 0;
    foreach ($updates as $update) {
        $queue_id = $update['queue_id'] ?? 0;
        $status = $update['status'] ?? '';
        $error_code = $update['error_code'] ?? null;
        $sent_timestamp = $update['sent_timestamp'] ?? null;
        $delivered_timestamp = $update['delivered_timestamp'] ?? null;
        
        // Update main table
        $sql = "UPDATE sms_queue SET 
                status = ?, 
                error_code = ?,
                sent_timestamp = ?,
                delivered_timestamp = ?,
                updated_at = NOW()
                WHERE queue_id = ?";
        
        $stmt = $pdo->prepare($sql);
        $stmt->execute([
            $status, 
            $error_code,
            $sent_timestamp,
            $delivered_timestamp,
            $queue_id
        ]);
        
        // Log to history table
        $sql = "INSERT INTO sms_status_history 
                (queue_id, status, error_code, timestamp) 
                VALUES (?, ?, ?, ?)";
        
        $stmt = $pdo->prepare($sql);
        $stmt->execute([
            $queue_id,
            $status,
            $error_code,
            $sent_timestamp ?: time() * 1000
        ]);
        
        $processed++;
        
        error_log("SMS Status Update: Queue $queue_id → $status");
    }
    
    echo json_encode([
        'status' => 'success',
        'message' => "Processed $processed SMS updates successfully",
        'results' => array_map(function($u) {
            return [
                'queue_id' => $u['queue_id'],
                'status_updated' => $u['status']
            ];
        }, $updates)
    ]);
}
?>
```

---

## 📊 Database Schema Recommendations

```sql
-- Main SMS queue table
CREATE TABLE sms_queue (
    queue_id INT AUTO_INCREMENT PRIMARY KEY,
    shipment_id VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    status ENUM('queued', 'processing', 'pending', 'sent', 'delivered', 'failed', 'sent_unconfirmed') DEFAULT 'queued',
    error_code VARCHAR(100),
    sent_timestamp BIGINT,
    delivered_timestamp BIGINT,
    delivery_time_seconds INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_shipment_id (shipment_id)
);

-- Status history for tracking all changes
CREATE TABLE sms_status_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    queue_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(100),
    timestamp BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_queue_id (queue_id),
    FOREIGN KEY (queue_id) REFERENCES sms_queue(queue_id)
);
```

---

## ✅ Checklist for Backend Implementation

- [ ] Handle GET requests returning SMS tasks with queue_id
- [ ] Handle POST requests for status updates
- [ ] Support both single object and array formats
- [ ] Store all status transitions (processing → pending → sent → delivered)
- [ ] Handle error_code field when status is "failed"
- [ ] Store timestamps in milliseconds
- [ ] Calculate delivery_time_seconds when both timestamps available
- [ ] Log all status changes for debugging
- [ ] Return success response for status updates
- [ ] Implement status history tracking

---

## 📞 Contact & Support

If you need clarification on any status or behavior:
1. Check the app logs for detailed status progression
2. Test with different phone numbers and carriers
3. Monitor the status updates being sent to your endpoint

**Remember**: The app is designed to be resilient and will continue operating even if your backend is temporarily unavailable. It will retry failed status updates automatically.

---

*Last Updated: September 2025*
*SMS Gateway App Version: 1.0.0*
*API Endpoint: https://mex.mk/api/sms/prober.php*