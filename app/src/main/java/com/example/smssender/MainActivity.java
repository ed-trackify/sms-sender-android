package com.example.smssender;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.app.ActivityManager;
import android.net.Uri;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    
    private static final int SMS_PERMISSION_CODE = 1;
    
    private Button probeButton;
    private EditText intervalInput;
    private EditText replyIntervalInput;
    private EditText phoneNumberInput;
    private TextView statusText;
    private TextView logText;
    private TextView permissionStatus;
    private TextView networkStatus;
    private TextView smsSentCount;
    private TextView smsPendingCount;
    private TextView smsFailedCount;
    private TextView totalRepliesCount;
    private TextView repliesQueuedCount;
    private TextView pinConfirmationsCount;
    private TextView lastReplySync;
    private TextView appNameText;
    private TextView appSubtitleText;
    private CheckBox enableLoggingCheckbox;
    private Button smsLogTab;
    private Button replyLogTab;
    private boolean showingSmsLog = true;
    private SharedPreferences settingsPrefs;
    private SharedPreferences replyLogPrefs;
    private int sentCounter = 0;
    private int pendingCounter = 0;
    private int failedCounter = 0;
    private SharedPreferences logPrefs;
    private SharedPreferences replyStatsPrefs;
    private Handler logUpdateHandler = new Handler();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private Handler handler = new Handler();
    private boolean isProbing = false;
    private Runnable probeRunnable;
    private BroadcastReceiver statisticsReceiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        probeButton = findViewById(R.id.probeButton);
        intervalInput = findViewById(R.id.intervalInput);
        replyIntervalInput = findViewById(R.id.replyIntervalInput);
        phoneNumberInput = findViewById(R.id.phoneNumberInput);
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        permissionStatus = findViewById(R.id.permissionStatus);
        networkStatus = findViewById(R.id.networkStatus);
        smsSentCount = findViewById(R.id.smsSentCount);
        smsPendingCount = findViewById(R.id.smsPendingCount);
        smsFailedCount = findViewById(R.id.smsFailedCount);
        totalRepliesCount = findViewById(R.id.totalRepliesCount);
        repliesQueuedCount = findViewById(R.id.repliesQueuedCount);
        pinConfirmationsCount = findViewById(R.id.pinConfirmationsCount);
        lastReplySync = findViewById(R.id.lastReplySync);
        appNameText = findViewById(R.id.appNameText);
        appSubtitleText = findViewById(R.id.appSubtitleText);
        enableLoggingCheckbox = findViewById(R.id.enableLoggingCheckbox);
        smsLogTab = findViewById(R.id.smsLogTab);
        replyLogTab = findViewById(R.id.replyLogTab);
        
        logPrefs = getSharedPreferences("SmsProbeLog", MODE_PRIVATE);
        replyLogPrefs = getSharedPreferences("ReplyLog", MODE_PRIVATE);
        replyStatsPrefs = getSharedPreferences("ReplyStats", MODE_PRIVATE);
        settingsPrefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        
        // Set app name and subtitle from config
        appNameText.setText(AppConfig.APP_NAME);
        appSubtitleText.setText(AppConfig.APP_SUBTITLE);
        
        // Set default intervals from config
        intervalInput.setText(String.valueOf(AppConfig.DEFAULT_PROBE_INTERVAL));
        replyIntervalInput.setText(String.valueOf(AppConfig.DEFAULT_REPLY_INTERVAL));
        
        // Load saved phone number or use default
        SharedPreferences appConfigPrefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
        String savedPhoneNumber = appConfigPrefs.getString("our_phone_number", AppConfig.DEFAULT_PHONE_NUMBER);
        phoneNumberInput.setText(savedPhoneNumber);
        
        // Set up logging checkbox
        boolean loggingEnabled = settingsPrefs.getBoolean("logging_enabled", AppConfig.LOGGING_ENABLED_DEFAULT);
        enableLoggingCheckbox.setChecked(loggingEnabled);
        enableLoggingCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsPrefs.edit().putBoolean("logging_enabled", isChecked).apply();
                if (!isChecked) {
                    logText.setText("Logging disabled");
                } else {
                    updateLog();
                }
            }
        });
        
        // Set up log tab buttons
        smsLogTab.setOnClickListener(v -> {
            showingSmsLog = true;
            smsLogTab.setBackgroundColor(getColor(R.color.md_theme_primary));
            smsLogTab.setTextColor(getColor(R.color.white));
            replyLogTab.setBackgroundColor(0xFFE0E0E0);
            replyLogTab.setTextColor(0xFF666666);
            updateLog();
        });
        
        replyLogTab.setOnClickListener(v -> {
            showingSmsLog = false;
            replyLogTab.setBackgroundColor(getColor(R.color.md_theme_primary));
            replyLogTab.setTextColor(getColor(R.color.white));
            smsLogTab.setBackgroundColor(0xFFE0E0E0);
            smsLogTab.setTextColor(0xFF666666);
            updateLog();
        });
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) 
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) 
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionStatus.setText("Not Granted");
            permissionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            ActivityCompat.requestPermissions(this, 
                new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                }, SMS_PERMISSION_CODE);
        } else {
            permissionStatus.setText("Granted");
            permissionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }
        
        // Update button state based on service status
        updateButtonState();
        
        // Request battery optimization exemption for continuous background operation
        requestBatteryOptimizationExemption();
        
        // Start updating logs from service
        startLogUpdates();

        // Register receiver for statistics updates
        registerStatisticsReceiver();

        probeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isServiceRunning()) {
                    startBackgroundService();
                } else {
                    stopBackgroundService();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (statisticsReceiver != null) {
            unregisterReceiver(statisticsReceiver);
        }
    }

    private void registerStatisticsReceiver() {
        statisticsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.smssender.UPDATE_STATISTICS".equals(intent.getAction())) {
                    sentCounter = intent.getIntExtra("sent_count", 0);
                    failedCounter = intent.getIntExtra("failed_count", 0);
                    int deliveredCount = intent.getIntExtra("delivered_count", 0);

                    runOnUiThread(() -> {
                        smsSentCount.setText(String.valueOf(sentCounter));
                        smsFailedCount.setText(String.valueOf(failedCounter));
                        smsPendingCount.setText(String.valueOf(deliveredCount));
                    });
                }
            }
        };

        IntentFilter filter = new IntentFilter("com.example.smssender.UPDATE_STATISTICS");
        registerReceiver(statisticsReceiver, filter);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateButtonState();
        updateLogsFromService();
        updateReplyStatistics();
    }
    
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            String packageName = getPackageName();
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
                    statusText.setText("Requesting battery optimization exemption for continuous operation");
                } catch (Exception e) {
                    // If the direct intent fails, open battery optimization settings
                    try {
                        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(intent);
                        statusText.setText("Please disable battery optimization for this app");
                    } catch (Exception ex) {
                        statusText.setText("Unable to request battery optimization exemption");
                    }
                }
            } else {
                statusText.setText("Battery optimizations already disabled - Service will run continuously");
            }
        }
    }
    
    private void startBackgroundService() {
        String intervalStr = intervalInput.getText().toString();
        String replyIntervalStr = replyIntervalInput.getText().toString();
        long interval = AppConfig.DEFAULT_PROBE_INTERVAL;
        long replyInterval = AppConfig.DEFAULT_REPLY_INTERVAL;
        
        // Validate probe interval
        try {
            interval = Long.parseLong(intervalStr);
            if (interval < AppConfig.MIN_PROBE_INTERVAL) {
                interval = AppConfig.MIN_PROBE_INTERVAL;
                intervalInput.setText(String.valueOf(AppConfig.MIN_PROBE_INTERVAL));
            } else if (interval > AppConfig.MAX_PROBE_INTERVAL) {
                interval = AppConfig.MAX_PROBE_INTERVAL;
                intervalInput.setText(String.valueOf(AppConfig.MAX_PROBE_INTERVAL));
            }
        } catch (Exception e) {
            interval = AppConfig.DEFAULT_PROBE_INTERVAL;
            intervalInput.setText(String.valueOf(AppConfig.DEFAULT_PROBE_INTERVAL));
        }
        
        // Validate reply interval
        try {
            replyInterval = Long.parseLong(replyIntervalStr);
            if (replyInterval < AppConfig.MIN_REPLY_INTERVAL) {
                replyInterval = AppConfig.MIN_REPLY_INTERVAL;
                replyIntervalInput.setText(String.valueOf(AppConfig.MIN_REPLY_INTERVAL));
            } else if (replyInterval > AppConfig.MAX_REPLY_INTERVAL) {
                replyInterval = AppConfig.MAX_REPLY_INTERVAL;
                replyIntervalInput.setText(String.valueOf(AppConfig.MAX_REPLY_INTERVAL));
            }
        } catch (Exception e) {
            replyInterval = AppConfig.DEFAULT_REPLY_INTERVAL;
            replyIntervalInput.setText(String.valueOf(AppConfig.DEFAULT_REPLY_INTERVAL));
        }
        
        // Save phone number
        String phoneNumber = phoneNumberInput.getText().toString().trim();
        if (!phoneNumber.isEmpty()) {
            SharedPreferences appConfigPrefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
            appConfigPrefs.edit().putString("our_phone_number", phoneNumber).apply();
        }
        
        // Save intervals to preferences
        SharedPreferences prefs = getSharedPreferences("sms_prober", MODE_PRIVATE);
        prefs.edit()
            .putLong("probe_interval", interval)
            .putLong("reply_interval", replyInterval)
            .apply();
        
        Intent serviceIntent = new Intent(this, SmsProbeService.class);
        serviceIntent.putExtra("interval", interval * 1000);
        serviceIntent.putExtra("reply_interval", replyInterval * 1000);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        updateStatus("Background service started");
        addLog("Started service (probe: " + interval + "s, reply: " + replyInterval + "s)");
        updateButtonState();
    }
    
    private void stopBackgroundService() {
        Intent serviceIntent = new Intent(this, SmsProbeService.class);
        stopService(serviceIntent);
        
        updateStatus("Background service stopped");
        addLog("Stopped background service");
        updateButtonState();
    }
    
    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SmsProbeService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    private void updateButtonState() {
        if (isServiceRunning()) {
            probeButton.setText("Stop Background Service");
            probeButton.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            statusText.setText("Status: Service Running");
        } else {
            probeButton.setText("Start Background Service");
            probeButton.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
            statusText.setText("Status: Service Stopped");
        }
    }
    
    private void startLogUpdates() {
        logUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateLogsFromService();
                updateReplyStatistics();
                logUpdateHandler.postDelayed(this, 2000); // Update every 2 seconds
            }
        }, 1000);
    }
    
    private void updateLogsFromService() {
        boolean loggingEnabled = settingsPrefs.getBoolean("logging_enabled", AppConfig.LOGGING_ENABLED_DEFAULT);
        if (!loggingEnabled) {
            logText.setText("Logging disabled");
            return;
        }
        
        String logs;
        if (showingSmsLog) {
            logs = logPrefs.getString("log", "");
        } else {
            logs = replyLogPrefs.getString("log", "");
        }
        
        if (!logs.isEmpty()) {
            logText.setText(logs);
        } else {
            logText.setText(showingSmsLog ? "No SMS activity logged yet..." : "No reply activity logged yet...");
        }
    }
    
    private void updateLog() {
        updateLogsFromService();
    }
    
    private void updateReplyStatistics() {
        // Update total replies
        int totalReplies = replyStatsPrefs.getInt("total_replies", 0);
        totalRepliesCount.setText(String.valueOf(totalReplies));
        
        // Update PIN confirmations
        int pinConfirmations = replyStatsPrefs.getInt("reply_count_pin_confirmation", 0);
        pinConfirmationsCount.setText(String.valueOf(pinConfirmations));
        
        // Update queue size
        try {
            ReplyQueueManager queueManager = ReplyQueueManager.getInstance(this);
            int queueSize = queueManager.getQueueSize();
            repliesQueuedCount.setText(String.valueOf(queueSize));
        } catch (Exception e) {
            repliesQueuedCount.setText("0");
        }
        
        // Update last sync time
        long lastSyncTime = replyStatsPrefs.getLong("last_reply_sync", 0);
        if (lastSyncTime > 0) {
            Date syncDate = new Date(lastSyncTime);
            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            lastReplySync.setText(format.format(syncDate));
        } else {
            lastReplySync.setText("Never");
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                updateStatus("All SMS permissions granted");
                permissionStatus.setText("Granted");
                permissionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                updateStatus("Some SMS permissions denied");
                permissionStatus.setText("Not Granted");
                permissionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        }
    }
    
    private void updateStatus(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText("Status: " + message);
            }
        });
    }
    
    private void addLog(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String timestamp = dateFormat.format(new Date());
                String currentLog = logText.getText().toString();
                if (currentLog.equals("Logs will appear here...")) {
                    currentLog = "";
                }
                logText.setText(currentLog + timestamp + " - " + message + "\n");
            }
        });
    }
    
    private class ProbeUrlTask extends AsyncTask<String, Void, String> {
        
        @Override
        protected void onPreExecute() {
            updateStatus("Probing URL...");
        }
        
        @Override
        protected String doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                
                int responseCode = connection.getResponseCode();
                addLog("Response Code: " + responseCode);
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    return response.toString();
                } else {
                    return null;
                }
            } catch (Exception e) {
                addLog("Error: " + e.getMessage());
                return null;
            }
        }
        
        @Override
        protected void onPostExecute(String result) {
            if (result != null && !result.trim().isEmpty()) {
                try {
                    JSONObject json = new JSONObject(result);
                    String phoneNumber = json.getString("phone");
                    String message = json.getString("message");
                    String shipmentId = json.getString("shipment_id");
                    
                    addLog("Shipment ID: " + shipmentId);
                    addLog("Phone: " + phoneNumber);
                    addLog("Message: " + message);
                    
                    sendSMS(phoneNumber, message, shipmentId);
                } catch (Exception e) {
                    updateStatus("JSON parsing error");
                    addLog("JSON Error: " + e.getMessage());
                }
            } else {
                updateStatus("No SMS to send");
            }
        }
    }
    
    private void sendSMS(String phoneNumber, String message, String shipmentId) {
        try {
            pendingCounter++;
            updateCounters();
            
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            updateStatus("SMS sent successfully");
            addLog("SMS sent to " + phoneNumber);
            
            pendingCounter--;
            sentCounter++;
            updateCounters();
            
            // Send response back to server
            new SendResponseTask().execute(shipmentId, message);
        } catch (Exception e) {
            updateStatus("Failed to send SMS");
            addLog("SMS Error: " + e.getMessage());
            pendingCounter--;
            failedCounter++;
            updateCounters();
        }
    }
    
    private void updateCounters() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                smsSentCount.setText(String.valueOf(sentCounter));
                smsPendingCount.setText(String.valueOf(pendingCounter));
                smsFailedCount.setText(String.valueOf(failedCounter));
            }
        });
    }
    
    private class SendResponseTask extends AsyncTask<String, Void, Boolean> {
        
        @Override
        protected Boolean doInBackground(String... params) {
            String shipmentId = params[0];
            String smsMessage = params[1];
            
            try {
                URL url = new URL(AppConfig.PROBE_ENDPOINT);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                
                String postData = "shipment_id=" + URLEncoder.encode(shipmentId, "UTF-8") +
                                 "&sms_sent=" + URLEncoder.encode(smsMessage, "UTF-8") +
                                 "&response=success";
                
                OutputStream os = connection.getOutputStream();
                os.write(postData.getBytes("UTF-8"));
                os.flush();
                os.close();
                
                int responseCode = connection.getResponseCode();
                addLog("Response sent, code: " + responseCode);
                
                return responseCode == HttpURLConnection.HTTP_OK;
            } catch (Exception e) {
                addLog("Response error: " + e.getMessage());
                return false;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                addLog("Response sent successfully");
            } else {
                addLog("Failed to send response");
            }
        }
    }
}