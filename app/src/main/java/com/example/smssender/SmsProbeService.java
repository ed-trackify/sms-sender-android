package com.example.smssender;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.SmsManager;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SmsProbeService extends Service {
    
    private static final String CHANNEL_ID = "SmsProbeServiceChannel";
    private static final String PROBE_URL = AppConfig.PROBE_ENDPOINT;
    private static final String API_KEY = AppConfig.API_KEY;
    private static final int NOTIFICATION_ID = 1;
    
    private Handler handler = new Handler();
    private Runnable probeRunnable;
    private boolean isRunning = false;
    private long probeInterval = 60000; // Default 60 seconds
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private PowerManager.WakeLock wakeLock;
    
    private int sentCounter = 0;
    private int failedCounter = 0;
    private int deliveredCounter = 0;
    
    // Track pending SMS for status updates
    private Map<Integer, PendingSms> pendingSmsMap = new HashMap<>();
    private List<JSONObject> pendingStatusUpdates = new ArrayList<>();
    
    private SmsSentReceiver smsSentReceiver;
    private SmsDeliveredReceiver smsDeliveredReceiver;
    
    // Class to track SMS details
    private static class PendingSms {
        int queueId;
        long shipmentId;
        String phone;
        String message;
        long sentTimestamp;
        
        PendingSms(int queueId, long shipmentId, String phone, String message) {
            this.queueId = queueId;
            this.shipmentId = shipmentId;
            this.phone = phone;
            this.message = message;
            this.sentTimestamp = System.currentTimeMillis();
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        // Acquire wake lock to keep CPU running even when screen is off
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SmsProbeService::WakeLock"
        );
        wakeLock.setReferenceCounted(false);
        
        // Acquire wake lock with no timeout - will be released in onDestroy
        wakeLock.acquire();
        
        // Register SMS broadcast receivers
        registerSmsReceivers();
    }
    
    private void registerSmsReceivers() {
        smsSentReceiver = new SmsSentReceiver();
        smsDeliveredReceiver = new SmsDeliveredReceiver();
        
        registerReceiver(smsSentReceiver, new IntentFilter("SMS_SENT"));
        registerReceiver(smsDeliveredReceiver, new IntentFilter("SMS_DELIVERED"));
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("interval")) {
            probeInterval = intent.getLongExtra("interval", 60000);
        }
        
        if (!isRunning) {
            isRunning = true;
            startForeground(NOTIFICATION_ID, createNotification());
            startProbing();
            
            // Start batch status update handler
            startBatchStatusUpdater();
            
            // Schedule automatic restart to ensure continuous operation
            scheduleServiceRestart();
        }
        
        return START_STICKY; // Service will restart if killed
    }
    
    private void scheduleServiceRestart() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent restartIntent = new Intent("com.example.smssender.RESTART_SERVICE");
            restartIntent.setClass(this, ServiceRestartReceiver.class);
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 
                1001, 
                restartIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Schedule a restart every 30 minutes to ensure service stays alive
            long intervalMillis = 30 * 60 * 1000; // 30 minutes
            long triggerTime = SystemClock.elapsedRealtime() + intervalMillis;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                );
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                );
            }
            
            logMessage("Service restart scheduled for continuous operation");
        } catch (Exception e) {
            logMessage("Failed to schedule service restart: " + e.getMessage());
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                AppConfig.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription(AppConfig.NOTIFICATION_CHANNEL_DESC);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, 
            notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        String intervalText = "Probing every " + (probeInterval / 1000) + " seconds";
        String statsText = "Sent: " + sentCounter + " | Delivered: " + deliveredCounter + " | Failed: " + failedCounter;
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Sender Active")
            .setContentText(intervalText)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(intervalText + "\n" + statsText))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification());
    }
    
    private void startProbing() {
        probeRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    // Ensure wake lock is held during probe
                    if (wakeLock != null && !wakeLock.isHeld()) {
                        try {
                            wakeLock.acquire();
                        } catch (Exception e) {
                            logMessage("Failed to reacquire wake lock: " + e.getMessage());
                        }
                    }
                    
                    probeUrl();
                    handler.postDelayed(this, probeInterval);
                }
            }
        };
        handler.post(probeRunnable);
    }
    
    private void probeUrl() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(PROBE_URL);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    
                    // Add API key authentication
                    connection.setRequestProperty("X-API-Key", API_KEY);
                    connection.setRequestProperty("Content-Type", "application/json");
                    
                    int responseCode = connection.getResponseCode();
                    logMessage("Probe response: " + responseCode);
                    
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        
                        String result = response.toString();
                        if (result != null && !result.trim().isEmpty()) {
                            processSmsRequest(result);
                        }
                    } else if (responseCode == 204) {
                        // No content - no pending SMS
                        logMessage("No pending SMS messages");
                    } else if (responseCode == 401) {
                        logMessage("Authentication failed - check API key");
                    }
                } catch (Exception e) {
                    logMessage("Probe Error: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void processSmsRequest(String jsonResponse) {
        try {
            JSONObject json = new JSONObject(jsonResponse);
            String phone = json.getString("phone");
            String message = json.getString("message");
            int queueId = json.getInt("queue_id");
            long shipmentId = json.getLong("shipment_id");
            
            logMessage("SMS Task - Queue: " + queueId + ", Phone: " + phone);
            
            // Store SMS details for tracking
            PendingSms pendingSms = new PendingSms(queueId, shipmentId, phone, message);
            pendingSmsMap.put(queueId, pendingSms);
            
            // Report status: processing
            reportStatus(queueId, phone, "processing", shipmentId, "", null, null, null, message);
            
            // Send SMS with tracking
            sendSmsWithTracking(queueId, phone, message, shipmentId);
            
            // Track shipment for reply correlation
            trackShipmentForReplies(phone, shipmentId, queueId);
            
        } catch (Exception e) {
            logMessage("JSON Error: " + e.getMessage());
        }
    }
    
    private void sendSmsWithTracking(int queueId, String phone, String message, long shipmentId) {
        try {
            // Report status: pending (starting to send)
            reportStatus(queueId, phone, "pending", shipmentId, "", null, null, null, message);
            
            SmsManager smsManager = SmsManager.getDefault();
            
            // Create pending intents for SMS tracking
            Intent sentIntent = new Intent("SMS_SENT");
            sentIntent.putExtra("queue_id", queueId);
            sentIntent.putExtra("phone", phone);
            sentIntent.putExtra("shipment_id", shipmentId);
            sentIntent.putExtra("message", message);
            
            Intent deliveryIntent = new Intent("SMS_DELIVERED");
            deliveryIntent.putExtra("queue_id", queueId);
            deliveryIntent.putExtra("phone", phone);
            deliveryIntent.putExtra("shipment_id", shipmentId);
            deliveryIntent.putExtra("message", message);
            
            PendingIntent sentPI = PendingIntent.getBroadcast(this, queueId, sentIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent deliveredPI = PendingIntent.getBroadcast(this, queueId + 10000, deliveryIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            // Send SMS
            smsManager.sendTextMessage(phone, null, message, sentPI, deliveredPI);
            
        } catch (Exception e) {
            logMessage("SMS Send Error: " + e.getMessage());
            failedCounter++;
            updateNotification();
            
            // Report failure
            long timestamp = System.currentTimeMillis();
            reportStatus(queueId, phone, "failed", shipmentId, e.getMessage(), timestamp, null, null, message);
        }
    }
    
    private void reportStatus(int queueId, String phone, String status, long shipmentId, 
                             String errorCode, Long sentTimestamp, Long deliveredTimestamp, 
                             Integer deliveryTimeSeconds, String smsSent) {
        try {
            JSONObject statusUpdate = new JSONObject();
            statusUpdate.put("queue_id", queueId);
            statusUpdate.put("phone", phone);
            statusUpdate.put("status", status);
            
            if (smsSent != null && !smsSent.isEmpty()) {
                statusUpdate.put("sms_sent", smsSent);
            }
            
            if (errorCode != null && !errorCode.isEmpty()) {
                statusUpdate.put("error_code", errorCode);
            }
            
            if (sentTimestamp != null) {
                statusUpdate.put("sent_timestamp", sentTimestamp);
            }
            
            if (deliveredTimestamp != null) {
                statusUpdate.put("delivered_timestamp", deliveredTimestamp);
            }
            
            if (deliveryTimeSeconds != null) {
                statusUpdate.put("delivery_time_seconds", deliveryTimeSeconds);
            }
            
            // Add to pending updates for batch processing
            synchronized (pendingStatusUpdates) {
                pendingStatusUpdates.add(statusUpdate);
            }
            
            // Send immediately if critical status
            if (status.equals("delivered") || status.equals("failed")) {
                sendBatchStatusUpdate();
            }
            
        } catch (Exception e) {
            logMessage("Failed to prepare status update: " + e.getMessage());
        }
    }
    
    private void startBatchStatusUpdater() {
        // Send batch updates every 10 seconds
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    sendBatchStatusUpdate();
                    handler.postDelayed(this, 10000); // Every 10 seconds
                }
            }
        }, 10000);
    }
    
    private void sendBatchStatusUpdate() {
        synchronized (pendingStatusUpdates) {
            if (pendingStatusUpdates.isEmpty()) {
                return;
            }
            
            final List<JSONObject> updates = new ArrayList<>(pendingStatusUpdates);
            pendingStatusUpdates.clear();
            
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Send as array if multiple, single object if one
                        String jsonBody;
                        if (updates.size() == 1) {
                            jsonBody = updates.get(0).toString();
                        } else {
                            JSONArray array = new JSONArray();
                            for (JSONObject update : updates) {
                                array.put(update);
                            }
                            jsonBody = array.toString();
                        }
                        
                        URL url = new URL(PROBE_URL);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("POST");
                        connection.setRequestProperty("X-API-Key", API_KEY);
                        connection.setRequestProperty("Content-Type", "application/json");
                        connection.setDoOutput(true);
                        
                        OutputStream os = connection.getOutputStream();
                        os.write(jsonBody.getBytes("UTF-8"));
                        os.flush();
                        os.close();
                        
                        int responseCode = connection.getResponseCode();
                        
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            logMessage("Status batch sent: " + updates.size() + " updates");
                        } else {
                            logMessage("Status update failed: " + responseCode);
                            // Re-add to pending for retry
                            synchronized (pendingStatusUpdates) {
                                pendingStatusUpdates.addAll(updates);
                            }
                        }
                        
                    } catch (Exception e) {
                        logMessage("Batch update error: " + e.getMessage());
                        // Re-add to pending for retry
                        synchronized (pendingStatusUpdates) {
                            pendingStatusUpdates.addAll(updates);
                        }
                    }
                }
            }).start();
        }
    }
    
    // SMS Sent Broadcast Receiver
    private class SmsSentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int queueId = intent.getIntExtra("queue_id", 0);
            String phone = intent.getStringExtra("phone");
            long shipmentId = intent.getLongExtra("shipment_id", 0);
            String message = intent.getStringExtra("message");
            long sentTimestamp = System.currentTimeMillis();
            
            PendingSms sms = pendingSmsMap.get(queueId);
            if (sms != null) {
                sms.sentTimestamp = sentTimestamp;
            }
            
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    logMessage("SMS sent: Queue " + queueId);
                    sentCounter++;
                    updateNotification();
                    reportStatus(queueId, phone, "sent", shipmentId, "", sentTimestamp, null, null, message);
                    break;
                    
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    failedCounter++;
                    updateNotification();
                    logMessage("SMS failed (Generic): Queue " + queueId);
                    reportStatus(queueId, phone, "failed", shipmentId, "GENERIC_FAILURE", sentTimestamp, null, null, message);
                    pendingSmsMap.remove(queueId);
                    break;
                    
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    failedCounter++;
                    updateNotification();
                    logMessage("SMS failed (No Service): Queue " + queueId);
                    reportStatus(queueId, phone, "failed", shipmentId, "NO_SERVICE", sentTimestamp, null, null, message);
                    pendingSmsMap.remove(queueId);
                    break;
                    
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    failedCounter++;
                    updateNotification();
                    logMessage("SMS failed (Null PDU): Queue " + queueId);
                    reportStatus(queueId, phone, "failed", shipmentId, "NULL_PDU", sentTimestamp, null, null, message);
                    pendingSmsMap.remove(queueId);
                    break;
                    
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    failedCounter++;
                    updateNotification();
                    logMessage("SMS failed (Radio Off): Queue " + queueId);
                    reportStatus(queueId, phone, "failed", shipmentId, "RADIO_OFF", sentTimestamp, null, null, message);
                    pendingSmsMap.remove(queueId);
                    break;
                    
                default:
                    failedCounter++;
                    updateNotification();
                    logMessage("SMS failed (Unknown): Queue " + queueId);
                    reportStatus(queueId, phone, "failed", shipmentId, "UNKNOWN_ERROR", sentTimestamp, null, null, message);
                    pendingSmsMap.remove(queueId);
                    break;
            }
        }
    }
    
    // SMS Delivered Broadcast Receiver
    private class SmsDeliveredReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int queueId = intent.getIntExtra("queue_id", 0);
            String phone = intent.getStringExtra("phone");
            long shipmentId = intent.getLongExtra("shipment_id", 0);
            String message = intent.getStringExtra("message");
            long deliveredTimestamp = System.currentTimeMillis();
            
            PendingSms sms = pendingSmsMap.get(queueId);
            Long sentTimestamp = sms != null ? sms.sentTimestamp : null;
            Integer deliveryTimeSeconds = null;
            
            if (sentTimestamp != null) {
                deliveryTimeSeconds = (int) ((deliveredTimestamp - sentTimestamp) / 1000);
            }
            
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    logMessage("SMS delivered: Queue " + queueId);
                    deliveredCounter++;
                    updateNotification();
                    reportStatus(queueId, phone, "delivered", shipmentId, "", 
                               sentTimestamp, deliveredTimestamp, deliveryTimeSeconds, message);
                    pendingSmsMap.remove(queueId);
                    break;
                    
                case Activity.RESULT_CANCELED:
                    logMessage("SMS delivery unconfirmed: Queue " + queueId);
                    reportStatus(queueId, phone, "sent_unconfirmed", shipmentId, "", 
                               sentTimestamp, null, null, message);
                    pendingSmsMap.remove(queueId);
                    break;
            }
        }
    }
    
    private void trackShipmentForReplies(String phone, long shipmentId, int queueId) {
        try {
            // Save shipment info for reply correlation
            SharedPreferences prefs = getSharedPreferences("ShipmentTracking", MODE_PRIVATE);
            JSONObject shipmentInfo = new JSONObject();
            shipmentInfo.put("shipment_id", shipmentId);
            shipmentInfo.put("queue_id", queueId);
            shipmentInfo.put("sent_timestamp", System.currentTimeMillis());
            
            prefs.edit().putString(phone, shipmentInfo.toString()).apply();
            
            // Also add to recent recipients for reply filtering
            SharedPreferences recipientPrefs = getSharedPreferences("SmsRecipients", MODE_PRIVATE);
            String recentNumbers = recipientPrefs.getString("recent_numbers", "");
            if (!recentNumbers.contains(phone)) {
                recentNumbers = phone + "," + recentNumbers;
                // Keep only last 100 numbers
                String[] numbers = recentNumbers.split(",");
                if (numbers.length > 100) {
                    StringBuilder trimmed = new StringBuilder();
                    for (int i = 0; i < 100; i++) {
                        trimmed.append(numbers[i]).append(",");
                    }
                    recentNumbers = trimmed.toString();
                }
                recipientPrefs.edit().putString("recent_numbers", recentNumbers).apply();
            }
        } catch (Exception e) {
            logMessage("Failed to track shipment: " + e.getMessage());
        }
    }
    
    private void logMessage(String message) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = timestamp + " - " + message;
        
        // Save to SharedPreferences for MainActivity to read
        SharedPreferences prefs = getSharedPreferences("SmsProbeLog", MODE_PRIVATE);
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
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacks(probeRunnable);
        
        // Send any remaining status updates
        sendBatchStatusUpdate();
        
        // Unregister receivers
        if (smsSentReceiver != null) {
            unregisterReceiver(smsSentReceiver);
        }
        if (smsDeliveredReceiver != null) {
            unregisterReceiver(smsDeliveredReceiver);
        }
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        stopForeground(true);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}