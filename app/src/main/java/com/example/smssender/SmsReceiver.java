package com.example.smssender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {
    
    private static final String TAG = "SmsReceiver";
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    
    @Override
    public void onReceive(Context context, Intent intent) {
        logMessage(context, "SMS broadcast received");
        
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                try {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    String format = bundle.getString("format");
                    
                    if (pdus != null) {
                        logMessage(context, "Processing " + pdus.length + " SMS PDUs");
                        for (Object pdu : pdus) {
                            SmsMessage smsMessage;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                            } else {
                                smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                            }
                            
                            if (smsMessage != null) {
                                String sender = smsMessage.getDisplayOriginatingAddress();
                                String body = smsMessage.getMessageBody();
                                logMessage(context, "SMS from " + sender + ": " + body);
                                processSmsReply(context, smsMessage);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing SMS: " + e.getMessage());
                    logMessage(context, "Error processing SMS: " + e.getMessage());
                }
            }
        }
    }
    
    private void processSmsReply(Context context, SmsMessage smsMessage) {
        String sender = smsMessage.getDisplayOriginatingAddress();
        String message = smsMessage.getMessageBody();
        long timestamp = smsMessage.getTimestampMillis();
        
        logMessage(context, "Reply from " + sender + ": " + message);
        
        // Check if this could be a reply to our SMS
        if (isRelevantReply(context, sender, message)) {
            try {
                // Create reply object
                JSONObject reply = new JSONObject();
                reply.put("phone_from", sender);
                reply.put("phone_to", getOurPhoneNumber(context));
                reply.put("message", message);
                reply.put("received_timestamp", timestamp);
                
                // Try to find related shipment
                JSONObject shipmentInfo = findRelatedShipment(context, sender);
                if (shipmentInfo != null) {
                    reply.put("shipment_id", shipmentInfo.getLong("shipment_id"));
                    reply.put("original_queue_id", shipmentInfo.getInt("queue_id"));
                    // Include the original message we sent
                    String originalMessage = shipmentInfo.optString("original_message", "");
                    if (!originalMessage.isEmpty()) {
                        reply.put("original_message", originalMessage);
                    }
                    // Include when we sent the original message
                    long sentTimestamp = shipmentInfo.optLong("sent_timestamp", 0);
                    if (sentTimestamp > 0) {
                        reply.put("original_sent_timestamp", sentTimestamp);
                    }
                }
                
                // Classify reply type
                String replyType = classifyReply(message);
                reply.put("reply_type", replyType);
                
                // Add device info
                JSONObject deviceInfo = new JSONObject();
                deviceInfo.put("android_version", Build.VERSION.RELEASE);
                deviceInfo.put("app_version", AppConfig.APP_VERSION);
                deviceInfo.put("device_id", getDeviceId(context));
                reply.put("device_info", deviceInfo);
                
                // Queue for sending to server
                ReplyQueueManager.getInstance(context).queueReply(reply);
                
                // Update statistics
                updateReplyStatistics(context, replyType);
                
                logMessage(context, "Reply queued: " + replyType);
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating reply object: " + e.getMessage());
            }
        }
    }
    
    private boolean isRelevantReply(Context context, String sender, String message) {
        // TEMPORARY: Process ALL SMS for debugging
        logMessage(context, "DEBUG: Processing ALL SMS (debugging mode)");
        return true;
        
        /* ORIGINAL CODE - Uncomment after debugging:
        // Check if sender is in our recent SMS recipients list
        SharedPreferences prefs = context.getSharedPreferences("SmsRecipients", Context.MODE_PRIVATE);
        String recentRecipients = prefs.getString("recent_numbers", "");
        
        // Always process if it contains keywords we're interested in
        if (containsRelevantKeywords(message)) {
            return true;
        }
        
        // Check if sender is in recent recipients
        return recentRecipients.contains(sender);
        */
    }
    
    private boolean containsRelevantKeywords(String message) {
        String upperMessage = message.toUpperCase();
        return upperMessage.contains("PIN") ||
               upperMessage.contains("STOP") ||
               upperMessage.contains("DELIVERED") ||
               upperMessage.contains("RECEIVED") ||
               upperMessage.contains("RESCHEDULE") ||
               upperMessage.contains("DELAY") ||
               upperMessage.contains("SHIPMENT") ||
               upperMessage.contains("PACKAGE");
    }
    
    private String classifyReply(String message) {
        String upperMessage = message.toUpperCase();
        
        // PIN confirmation pattern
        Pattern pinPattern = Pattern.compile("\\bPIN:?\\s*(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
        if (pinPattern.matcher(message).find()) {
            return "pin_confirmation";
        }
        
        // Opt-out patterns (Serbian: STOP, ODJAVI, PREKINI, OTKAŽI)
        if (upperMessage.matches(".*(STOP|UNSUBSCRIBE|CANCEL|QUIT|END|ODJAVI|PREKINI|OTKAŽI|OTKAZI).*")) {
            return "opt_out";
        }
        
        // Delivery confirmation (Serbian: DOSTAVLJENO, PRIMLJENO, PREUZETO)
        if (upperMessage.matches(".*(DELIVERED|RECEIVED|GOT IT|COLLECTED|PICKED UP|DOSTAVLJENO|PRIMLJENO|PREUZETO).*")) {
            return "delivery_confirmation";
        }
        
        // Reschedule request (Serbian: ODLOŽI, KASNIJE, SUTRA, POMERI)
        if (upperMessage.matches(".*(RESCHEDULE|POSTPONE|DELAY|LATER|TOMORROW|ODLOŽI|ODLOZI|KASNIJE|SUTRA|POMERI).*")) {
            return "reschedule_request";
        }
        
        // Complaint or issue (Serbian: PROBLEM, GREŠKA, POGREŠNO, ŽALBA)
        if (upperMessage.matches(".*(PROBLEM|ISSUE|WRONG|ERROR|MISTAKE|COMPLAINT|GREŠKA|GRESKA|POGREŠNO|POGRESNO|ŽALBA|ZALBA).*")) {
            return "complaint";
        }
        
        // Question (Serbian: KADA, GDE, KAKO, ŠTA, ZAŠTO)
        if (message.contains("?") || upperMessage.matches(".*(WHEN|WHERE|HOW|WHAT|WHY|KADA|GDE|GDJE|KAKO|ŠTA|STA|ZAŠTO|ZASTO).*")) {
            return "general_inquiry";
        }
        
        return "unknown";
    }
    
    private JSONObject findRelatedShipment(Context context, String phoneNumber) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("ShipmentTracking", Context.MODE_PRIVATE);
            String trackingData = prefs.getString(phoneNumber, null);
            
            if (trackingData != null) {
                JSONObject data = new JSONObject(trackingData);
                // Check if recent (within 24 hours)
                long sentTime = data.getLong("sent_timestamp");
                long now = System.currentTimeMillis();
                if (now - sentTime < 24 * 60 * 60 * 1000) { // 24 hours
                    return data;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding shipment: " + e.getMessage());
        }
        return null;
    }
    
    private String getOurPhoneNumber(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        return prefs.getString("our_phone_number", AppConfig.DEFAULT_PHONE_NUMBER);
    }
    
    private String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        String deviceId = prefs.getString("device_id", null);
        
        if (deviceId == null) {
            // Generate a unique device ID
            deviceId = "AND_" + System.currentTimeMillis() + "_" + Build.DEVICE;
            prefs.edit().putString("device_id", deviceId).apply();
        }
        
        return deviceId;
    }
    
    private void updateReplyStatistics(Context context, String replyType) {
        SharedPreferences prefs = context.getSharedPreferences("ReplyStats", Context.MODE_PRIVATE);
        String key = "reply_count_" + replyType;
        int currentCount = prefs.getInt(key, 0);
        prefs.edit().putInt(key, currentCount + 1).apply();
        
        // Update total count
        int totalCount = prefs.getInt("total_replies", 0);
        prefs.edit().putInt("total_replies", totalCount + 1).apply();
    }
    
    private void logMessage(Context context, String message) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = timestamp + " - [REPLY] " + message;
        
        SharedPreferences prefs = context.getSharedPreferences("SmsProbeLog", Context.MODE_PRIVATE);
        String currentLog = prefs.getString("log", "");
        String newLog = logEntry + "\n" + currentLog;
        
        // Keep only last 100 lines
        String[] lines = newLog.split("\n");
        if (lines.length > 100) {
            StringBuilder trimmedLog = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                trimmedLog.append(lines[i]).append("\n");
            }
            newLog = trimmedLog.toString();
        }
        
        prefs.edit().putString("log", newLog).apply();
        Log.d(TAG, logEntry);
    }
}