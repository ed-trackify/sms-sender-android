package com.example.smssender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class ServiceRestartReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceRestartReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Service restart broadcast received: " + intent.getAction());
        
        if (intent != null) {
            String action = intent.getAction();
            
            if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
                "com.example.smssender.RESTART_SERVICE".equals(action)) {
                
                try {
                    Intent serviceIntent = new Intent(context, SmsProbeService.class);
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    
                    Log.d(TAG, "Service restarted successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to restart service: " + e.getMessage());
                }
            }
        }
    }
}