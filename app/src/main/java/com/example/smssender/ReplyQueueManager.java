package com.example.smssender;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ReplyQueueManager {
    
    private static final String TAG = "ReplyQueueManager";
    private static final String REPLY_URL = AppConfig.REPLY_ENDPOINT;
    private static final String API_KEY = AppConfig.API_KEY;
    private static final int BATCH_SIZE = AppConfig.REPLY_BATCH_SIZE;
    private static final long DEFAULT_BATCH_INTERVAL = AppConfig.REPLY_BATCH_INTERVAL;
    
    private static ReplyQueueManager instance;
    private Context context;
    private List<JSONObject> replyQueue;
    private Handler handler;
    private boolean isProcessing = false;
    private long batchInterval = DEFAULT_BATCH_INTERVAL;
    private Runnable batchProcessor;
    
    private ReplyQueueManager(Context context) {
        this.context = context.getApplicationContext();
        this.replyQueue = new ArrayList<>();
        this.handler = new Handler(Looper.getMainLooper());
        loadQueueFromStorage();
        startBatchProcessor();
    }
    
    public static synchronized ReplyQueueManager getInstance(Context context) {
        if (instance == null) {
            instance = new ReplyQueueManager(context);
        }
        return instance;
    }
    
    public synchronized void queueReply(JSONObject reply) {
        replyQueue.add(reply);
        saveQueueToStorage();
        
        try {
            String phoneFrom = reply.optString("phone_from", "Unknown");
            String replyType = reply.optString("reply_type", "unknown");
            addToLog("Reply queued from " + phoneFrom + " (Type: " + replyType + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error logging reply queue: " + e.getMessage());
        }
        
        // If queue is getting large, process immediately
        if (replyQueue.size() >= BATCH_SIZE) {
            processBatch();
        }
    }
    
    private void startBatchProcessor() {
        // Load interval from preferences
        SharedPreferences prefs = context.getSharedPreferences("sms_prober", Context.MODE_PRIVATE);
        long replyInterval = prefs.getLong("reply_interval", AppConfig.DEFAULT_REPLY_INTERVAL);
        batchInterval = replyInterval * 1000; // Convert seconds to milliseconds
        
        // Create the batch processor runnable
        batchProcessor = new Runnable() {
            @Override
            public void run() {
                processBatch();
                handler.postDelayed(this, batchInterval);
            }
        };
        
        handler.postDelayed(batchProcessor, batchInterval);
        addToLog("Reply processor started (checks every " + (batchInterval/1000) + "s)");
    }
    
    public void updateInterval(long intervalSeconds) {
        // Update the batch interval
        batchInterval = intervalSeconds * 1000;
        
        // Cancel current processor and restart with new interval
        if (batchProcessor != null) {
            handler.removeCallbacks(batchProcessor);
            startBatchProcessor();
        }
        
        addToLog("Reply interval updated to " + intervalSeconds + "s");
    }
    
    private synchronized void processBatch() {
        if (isProcessing || replyQueue.isEmpty()) {
            return;
        }
        
        isProcessing = true;
        
        // Create batch to send
        final List<JSONObject> batch = new ArrayList<>();
        int batchCount = Math.min(replyQueue.size(), BATCH_SIZE);
        
        for (int i = 0; i < batchCount; i++) {
            batch.add(replyQueue.get(0));
            replyQueue.remove(0);
        }
        
        // Save updated queue
        saveQueueToStorage();
        
        addToLog("Processing batch of " + batchCount + " replies (Queue remaining: " + replyQueue.size() + ")");
        
        // Send batch in background
        new Thread(new Runnable() {
            @Override
            public void run() {
                sendBatchToServer(batch);
                isProcessing = false;
            }
        }).start();
    }
    
    private void sendBatchToServer(List<JSONObject> batch) {
        try {
            // Prepare request body
            String jsonBody;
            if (batch.size() == 1) {
                jsonBody = batch.get(0).toString();
            } else {
                JSONArray array = new JSONArray();
                for (JSONObject reply : batch) {
                    array.put(reply);
                }
                jsonBody = array.toString();
            }
            
            // Send to server
            URL url = new URL(REPLY_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-API-Key", API_KEY);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(AppConfig.CONNECTION_TIMEOUT);
            connection.setReadTimeout(AppConfig.READ_TIMEOUT);
            
            OutputStream os = connection.getOutputStream();
            os.write(jsonBody.getBytes("UTF-8"));
            os.flush();
            os.close();
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Successfully sent " + batch.size() + " replies to server");
                addToLog("✓ Sent " + batch.size() + " replies to server successfully");
                updateStatistics(batch.size(), 0);
            } else {
                Log.e(TAG, "Failed to send replies. Response code: " + responseCode);
                addToLog("✗ Failed to send " + batch.size() + " replies (Code: " + responseCode + ")");
                // Re-queue failed items
                requeueFailedBatch(batch);
                updateStatistics(0, batch.size());
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending replies: " + e.getMessage());
            addToLog("✗ Error sending " + batch.size() + " replies: " + e.getMessage());
            // Re-queue failed items
            requeueFailedBatch(batch);
            updateStatistics(0, batch.size());
        }
    }
    
    private synchronized void requeueFailedBatch(List<JSONObject> batch) {
        // Add failed items back to the beginning of the queue
        for (int i = batch.size() - 1; i >= 0; i--) {
            replyQueue.add(0, batch.get(i));
        }
        saveQueueToStorage();
        addToLog("Re-queued " + batch.size() + " failed replies for retry");
    }
    
    private void saveQueueToStorage() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("ReplyQueue", Context.MODE_PRIVATE);
            JSONArray array = new JSONArray();
            for (JSONObject reply : replyQueue) {
                array.put(reply);
            }
            prefs.edit().putString("queue", array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving queue: " + e.getMessage());
        }
    }
    
    private void loadQueueFromStorage() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("ReplyQueue", Context.MODE_PRIVATE);
            String queueData = prefs.getString("queue", "[]");
            JSONArray array = new JSONArray(queueData);
            
            replyQueue.clear();
            for (int i = 0; i < array.length(); i++) {
                replyQueue.add(array.getJSONObject(i));
            }
            
            Log.d(TAG, "Loaded " + replyQueue.size() + " replies from storage");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading queue: " + e.getMessage());
        }
    }
    
    private void updateStatistics(int successCount, int failedCount) {
        SharedPreferences prefs = context.getSharedPreferences("ReplyStats", Context.MODE_PRIVATE);
        
        if (successCount > 0) {
            int totalSent = prefs.getInt("replies_sent_to_server", 0);
            prefs.edit().putInt("replies_sent_to_server", totalSent + successCount).apply();
        }
        
        if (failedCount > 0) {
            int totalFailed = prefs.getInt("replies_failed_to_send", 0);
            prefs.edit().putInt("replies_failed_to_send", totalFailed + failedCount).apply();
        }
        
        // Update last sync time on success
        if (successCount > 0) {
            prefs.edit().putLong("last_reply_sync", System.currentTimeMillis()).apply();
        }
    }
    
    public synchronized int getQueueSize() {
        return replyQueue.size();
    }
    
    public void forceSync() {
        processBatch();
    }
    
    private void addToLog(String message) {
        try {
            // Check if logging is enabled
            SharedPreferences settingsPrefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
            boolean loggingEnabled = settingsPrefs.getBoolean("logging_enabled", AppConfig.LOGGING_ENABLED_DEFAULT);
            if (!loggingEnabled) {
                return;
            }
            
            // Use separate log for replies
            SharedPreferences prefs = context.getSharedPreferences("ReplyLog", Context.MODE_PRIVATE);
            String existingLog = prefs.getString("log", "");
            
            // Add timestamp
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            String newEntry = timestamp + " | " + message;
            
            // Combine with existing log
            String updatedLog = newEntry + "\n" + existingLog;
            
            // Keep only last MAX_LOG_ENTRIES lines or MAX_LOG_SIZE characters, whichever is smaller
            String[] lines = updatedLog.split("\n");
            StringBuilder trimmedLog = new StringBuilder();
            int lineCount = 0;
            int charCount = 0;
            
            for (String line : lines) {
                if (lineCount >= AppConfig.MAX_LOG_ENTRIES || charCount + line.length() > AppConfig.MAX_LOG_SIZE) {
                    break;
                }
                trimmedLog.append(line).append("\n");
                lineCount++;
                charCount += line.length() + 1;
            }
            
            prefs.edit().putString("log", trimmedLog.toString()).apply();
            
        } catch (Exception e) {
            Log.e(TAG, "Error adding to log: " + e.getMessage());
        }
    }
}