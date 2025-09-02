# SMS Gateway Service Reliability Guide

## Why the Service Might Stop

### Common Interruption Causes

1. **Phone Calls**
   - **Impact**: SMS operations pause during calls
   - **Recovery**: Automatic after call ends
   - **Solution**: Service continues running, operations resume

2. **Low Memory (RAM)**
   - **Impact**: Android kills background services
   - **Frequency**: Common on devices <3GB RAM
   - **Solution**: START_STICKY ensures restart, but with delay

3. **Battery Optimization**
   - **Impact**: Doze mode suspends activities
   - **Worst Offenders**: Xiaomi, Huawei, Samsung, Oppo, Vivo
   - **Solution**: Disable battery optimization for the app

4. **System Updates/Reboots**
   - **Impact**: All services stop
   - **Recovery**: Boot receiver restarts service
   - **Note**: May take 1-2 minutes after boot

5. **Aggressive Task Killers**
   - **Impact**: Force stops all processes
   - **Sources**: Built-in cleaners, third-party apps
   - **Solution**: Whitelist app in task killer

## Current Protection Mechanisms

The app implements multiple layers of protection:

```
├── Foreground Service (High Priority)
├── Wake Lock (Prevents Sleep)
├── START_STICKY (Auto-restart)
├── Boot Receiver (Restart on boot)
├── Scheduled Restart (Every 30 min)
└── Crash Recovery (Exception handling)
```

## How to Maximize Reliability

### Device Settings (IMPORTANT)

1. **Disable Battery Optimization**
   ```
   Settings → Apps → SMS Gateway → Battery → Unrestricted
   ```

2. **Allow Background Activity**
   ```
   Settings → Apps → SMS Gateway → Battery → Allow background activity ✓
   ```

3. **Disable Data Saver**
   ```
   Settings → Network → Data Saver → Unrestricted data for SMS Gateway
   ```

4. **Lock App in Memory** (if available)
   ```
   Recent Apps → Long press SMS Gateway → Lock
   ```

### Manufacturer-Specific Settings

#### Xiaomi/Redmi/POCO
- Settings → Battery → App Battery Saver → SMS Gateway → No restrictions
- Settings → Permissions → Autostart → Enable SMS Gateway
- Developer Options → Memory optimization → Turn OFF

#### Samsung
- Settings → Device Care → Battery → Background usage limits → Never sleeping apps → Add SMS Gateway
- Settings → Device Care → Battery → Power mode → Optimized (not Maximum)

#### Huawei/Honor
- Settings → Battery → App Launch → SMS Gateway → Manage manually (all three options ON)
- Settings → Battery → Power Plan → Performance mode

#### OnePlus
- Settings → Battery → Battery Optimization → SMS Gateway → Don't optimize
- Settings → Apps → Special Access → Battery optimization → SMS Gateway → Don't optimize

#### Oppo/Realme
- Settings → Battery → Energy Saver → SMS Gateway → Turn OFF
- Settings → Additional Settings → Autostart → SMS Gateway → ON

#### Vivo
- Settings → Battery → Background power consumption → SMS Gateway → Allow
- Settings → Permissions → Autostart → SMS Gateway → Allow

## Monitoring Service Health

### In-App Indicators

1. **Green Status**: Service running normally
2. **Yellow Status**: Service running but issues detected
3. **Red Status**: Service stopped or critical error

### Check Points

- **Last Probe Time**: Should update every 60 seconds
- **Reply Queue Status**: Should show "checking for replies" every 30 seconds
- **Wake Lock Status**: Should show "Held"
- **Network Status**: Should show "Connected"

## Troubleshooting

### Service Stops Frequently

1. **Check RAM Usage**
   ```
   Settings → Developer Options → Running Services
   ```
   - If <500MB free, close other apps

2. **Check Battery Settings**
   - Ensure all optimizations disabled
   - Check manufacturer-specific settings above

3. **Check for Interfering Apps**
   - Disable task killers
   - Uninstall battery "optimizers"
   - Check antivirus exceptions

### Service Doesn't Restart

1. **Manual Restart**
   - Open app → Stop Service → Start Service

2. **Force Stop and Restart**
   ```
   Settings → Apps → SMS Gateway → Force Stop → Open app → Start Service
   ```

3. **Reboot Device**
   - Service should start automatically after boot
   - Wait 2 minutes for full initialization

### SMS Not Sending During Calls

This is **normal Android behavior**. Solutions:
- Wait for call to end
- Use dual-SIM with dedicated data SIM
- Enable VoLTE/VoWiFi if available

## Advanced Solutions

### For Critical Deployments

1. **Use Work Profile**
   - Isolates app from personal profile restrictions
   - Better protection from task killers

2. **Device Administrator Mode**
   - Prevents accidental uninstall
   - Higher system priority

3. **Custom ROM**
   - Use Android Open Source Project (AOSP) based ROM
   - Avoid manufacturer restrictions

4. **Dedicated Device**
   - Use device solely for SMS gateway
   - Minimal apps = maximum reliability
   - Recommended: 4GB+ RAM, stock Android

## Monitoring Scripts

### Server-Side Health Check

```php
// Add to your backend
function checkGatewayHealth($deviceId) {
    $lastProbe = getLastProbeTime($deviceId);
    $timeSinceProbe = time() - $lastProbe;
    
    if ($timeSinceProbe > 120) { // 2 minutes
        sendAlert("SMS Gateway $deviceId appears offline");
        return false;
    }
    return true;
}
```

### Auto-Recovery Webhook

```php
// Endpoint to trigger from monitoring
function triggerServiceRestart($deviceId) {
    // Send high-priority push notification to device
    // App can listen for this and restart service
    sendPushNotification($deviceId, [
        'action' => 'restart_service',
        'priority' => 'high'
    ]);
}
```

## Best Practices

1. **Regular Monitoring**
   - Check app daily for status
   - Monitor server logs for gaps
   - Set up alerts for extended downtime

2. **Preventive Maintenance**
   - Restart service weekly
   - Clear app cache monthly
   - Update app when available

3. **Backup Gateway**
   - Use multiple devices for redundancy
   - Rotate active device if issues
   - Keep spare device configured

## Known Limitations

1. **Cannot Override System**
   - Android 13+ has stricter background limits
   - Manufacturer ROMs can always override
   - Phone calls always take priority

2. **Network Dependencies**
   - Requires stable internet
   - Cellular network required for SMS
   - Both must work simultaneously

3. **Hardware Constraints**
   - Low-end devices struggle with 24/7 operation
   - Battery degradation affects reliability
   - Thermal throttling in hot conditions

## Recommended Devices

### Best Performance
- Google Pixel (any model)
- Nokia (Android One devices)
- Motorola G-series
- OnePlus Nord series

### Avoid If Possible
- Xiaomi/Redmi (aggressive killing)
- Huawei (heavy restrictions)
- Samsung (complex battery settings)
- Chinese brands with custom ROMs

## Support Checklist

If service stops working:

- [ ] Check if app is running
- [ ] Verify battery optimization is OFF
- [ ] Confirm permissions are granted
- [ ] Check network connectivity
- [ ] Review manufacturer-specific settings
- [ ] Check for system updates
- [ ] Look for crash logs in app
- [ ] Restart device if needed
- [ ] Reinstall app as last resort

---

*Last Updated: January 2025*
*App Version: 1.0.0*
*For critical issues, check device logs via ADB*