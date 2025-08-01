package com.example.smssender;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
    private static final String PROBE_URL = "https://eds-ks.com/api/sms_prober.php";
    private static final long PROBE_INTERVAL = 60000; // 60 seconds
    
    private EditText urlInput;
    private Button probeButton;
    private TextView statusText;
    private TextView logText;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private Handler handler = new Handler();
    private boolean isProbing = false;
    private Runnable probeRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        urlInput = findViewById(R.id.urlInput);
        probeButton = findViewById(R.id.probeButton);
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        
        urlInput.setText(PROBE_URL);
        urlInput.setEnabled(false);
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_CODE);
        }
        
        probeRunnable = new Runnable() {
            @Override
            public void run() {
                if (isProbing) {
                    new ProbeUrlTask().execute(PROBE_URL);
                    handler.postDelayed(this, PROBE_INTERVAL);
                }
            }
        };
        
        probeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isProbing) {
                    startProbing();
                } else {
                    stopProbing();
                }
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProbing();
    }
    
    private void startProbing() {
        isProbing = true;
        probeButton.setText("Stop Probing");
        updateStatus("Started probing every 60 seconds");
        addLog("Started automatic probing");
        handler.post(probeRunnable);
    }
    
    private void stopProbing() {
        isProbing = false;
        probeButton.setText("Start Probing");
        updateStatus("Stopped probing");
        addLog("Stopped automatic probing");
        handler.removeCallbacks(probeRunnable);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateStatus("SMS permission granted");
            } else {
                updateStatus("SMS permission denied");
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
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            updateStatus("SMS sent successfully");
            addLog("SMS sent to " + phoneNumber);
            
            // Send response back to server
            new SendResponseTask().execute(shipmentId, message);
        } catch (Exception e) {
            updateStatus("Failed to send SMS");
            addLog("SMS Error: " + e.getMessage());
        }
    }
    
    private class SendResponseTask extends AsyncTask<String, Void, Boolean> {
        
        @Override
        protected Boolean doInBackground(String... params) {
            String shipmentId = params[0];
            String smsMessage = params[1];
            
            try {
                URL url = new URL(PROBE_URL);
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