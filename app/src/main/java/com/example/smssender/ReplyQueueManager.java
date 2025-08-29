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
    private static final long BATCH_INTERVAL = AppConfig.REPLY_BATCH_INTERVAL;
    
    private static ReplyQueueManager instance;
    private Context context;
    private List<JSONObject> replyQueue;
    private Handler handler;
    private boolean isProcessing = false;
    
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
        
        // If queue is getting large, process immediately
        if (replyQueue.size() >= BATCH_SIZE) {
            processBatch();
        }
    }
    
    private void startBatchProcessor() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                processBatch();
                handler.postDelayed(this, BATCH_INTERVAL);
            }
        }, BATCH_INTERVAL);
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
                updateStatistics(batch.size(), 0);
            } else {
                Log.e(TAG, "Failed to send replies. Response code: " + responseCode);
                // Re-queue failed items
                requeueFailedBatch(batch);
                updateStatistics(0, batch.size());
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending replies: " + e.getMessage());
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
}