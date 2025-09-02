# Backend API Update - January 2025

## Overview
The Android SMS Gateway app has been updated with enhanced reply tracking capabilities. This document describes the new fields being sent to the `/api/sms/reply_handler.php` endpoint that your backend should handle.

## Updated Reply Handler Payload

### New Fields Added to Reply JSON

The app now sends additional context information to help link replies with their original SMS messages:

```json
{
  "phone_from": "+38970735418",
  "phone_to": "+38970000000",
  "message": "Ako moze da mi kazete iznos za placanje",
  "received_timestamp": 1756748428000,
  "shipment_id": 2632429,
  "original_queue_id": 1072,
  "original_message": "Your delivery arrives tomorrow. PIN: 4567",  // NEW
  "original_sent_timestamp": 1756748400000,                        // NEW
  "reply_type": "inquiry",
  "device_info": {
    "android_version": "10",
    "app_version": "1.0.0",
    "device_id": "AND_1756750008159_dandelion"
  }
}
```

### New Fields Description

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `original_message` | string | The complete text of the SMS we originally sent to this recipient | "Your delivery arrives tomorrow. PIN: 4567" |
| `original_sent_timestamp` | long | Unix timestamp (milliseconds) when the original SMS was sent | 1756748400000 |
| `phone_to` | string | The sender's phone number (device SIM number) - now configurable by user | "+38970000000" |

### Field Availability

- **`original_message`**: Only included when the reply can be matched to an original SMS sent by the app
- **`original_sent_timestamp`**: Only included when the reply can be matched to an original SMS
- **`phone_to`**: Now reflects the actual device phone number configured by the user (previously was hardcoded)

## Backend Recommendations

### 1. Database Schema Update

Consider adding these columns to your replies table:

```sql
ALTER TABLE sms_replies ADD COLUMN original_message TEXT DEFAULT NULL;
ALTER TABLE sms_replies ADD COLUMN original_sent_timestamp BIGINT DEFAULT NULL;
ALTER TABLE sms_replies ADD COLUMN response_time_seconds INT GENERATED ALWAYS AS 
  ((received_timestamp - original_sent_timestamp) / 1000) STORED;
```

### 2. Enhanced Reply Processing

With the original message available, you can now:

- **Better classify replies**: Know exactly what question/prompt the user is responding to
- **Calculate response times**: `received_timestamp - original_sent_timestamp` gives exact response time
- **Context-aware routing**: Route replies based on the original message content
- **Improved PIN verification**: Verify PINs against the specific PIN sent in the original message

### 3. Example PHP Handler Update

```php
// In your reply_handler.php
$data = json_decode($input, true);

// New fields to process
$original_message = $data['original_message'] ?? null;
$original_sent_timestamp = $data['original_sent_timestamp'] ?? null;
$phone_to = $data['phone_to'] ?? null;

// Calculate response time if timestamps available
$response_time_seconds = null;
if ($original_sent_timestamp && $data['received_timestamp']) {
    $response_time_seconds = ($data['received_timestamp'] - $original_sent_timestamp) / 1000;
}

// Enhanced PIN verification with original message
if ($data['reply_type'] === 'pin_confirmation' && $original_message) {
    // Extract PIN from original message
    preg_match('/PIN:\s*(\d+)/', $original_message, $matches);
    $expected_pin = $matches[1] ?? null;
    
    // Extract PIN from reply
    preg_match('/PIN:\s*(\d+)/', $data['message'], $reply_matches);
    $received_pin = $reply_matches[1] ?? null;
    
    if ($expected_pin && $received_pin) {
        $pin_valid = ($expected_pin === $received_pin);
        // Process based on PIN validation
    }
}

// Store in database with new fields
$stmt = $pdo->prepare("
    INSERT INTO sms_replies (
        phone_from, phone_to, message, received_timestamp,
        shipment_id, original_queue_id, original_message,
        original_sent_timestamp, reply_type, device_info
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
");
$stmt->execute([
    $data['phone_from'],
    $phone_to,
    $data['message'],
    $data['received_timestamp'],
    $data['shipment_id'] ?? null,
    $data['original_queue_id'] ?? null,
    $original_message,
    $original_sent_timestamp,
    $data['reply_type'],
    json_encode($data['device_info'])
]);
```

## Benefits of These Updates

1. **Complete Context**: Every reply now includes what it's responding to
2. **Accurate Metrics**: Calculate exact response times for delivery confirmations
3. **Better Classification**: Knowing the original message helps classify ambiguous replies
4. **Improved Debugging**: Full conversation context makes troubleshooting easier
5. **Enhanced Analytics**: Track which message types get the most/fastest responses

## Backward Compatibility

- All new fields are optional and may not be present in all replies
- Replies to SMS sent before this update won't have `original_message` or `original_sent_timestamp`
- The `phone_to` field now reflects user configuration instead of the hardcoded default

## Testing Recommendations

1. **Test with missing fields**: Ensure your handler works when new fields are absent
2. **Test PIN verification**: Verify PINs match between original and reply messages
3. **Test response time calculation**: Verify response times are calculated correctly
4. **Monitor field population**: Track what percentage of replies include the new fields

## Migration Timeline

- **Immediate**: App update deployed with new fields
- **Phase 1**: Backend should start capturing new fields (optional storage)
- **Phase 2**: Implement enhanced processing logic using new fields
- **Phase 3**: Update reporting/analytics to leverage new data

## Support

For questions about the new fields or integration issues:
- Check app logs for field population
- Verify replies are being matched (check for shipment_id presence)
- Ensure phone numbers are properly configured in the app

---

*Document created: January 2025*  
*App version: 1.0.0*  
*API endpoint: /api/sms/reply_handler.php*