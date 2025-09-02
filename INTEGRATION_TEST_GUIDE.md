# SMS Gateway Integration Test Guide

## ✅ Backend Confirmed Ready
The backend team has confirmed the SMS Reply Handler is **100% complete** as of December 28, 2024.

---

## 🚀 Quick Test Checklist

### 1. Prerequisites
- [ ] Install APK on test device with SIM card
- [ ] Grant all SMS permissions when prompted
- [ ] Ensure device has internet connection
- [ ] Have another phone to send test replies

### 2. Configuration Verification
The app is pre-configured with:
- **API Endpoint**: `https://srb.trackify.net/api/sms/`
- **API Key**: `osafu2379jsaf`
- **Reply Handler**: `/api/sms/reply_handler.php`

---

## 📱 Testing Procedures

### Test 1: Basic Service Operation
1. **Start the service**
   - Open app
   - Set probe interval to 60 seconds
   - Tap "Start Background Service"
   - ✅ Verify notification appears
   - ✅ Verify service status shows "Running"

2. **Check API connectivity**
   - Watch the Activity Log
   - ✅ Should show "Probe response: 200" or "Probe response: 204"
   - ✅ Should NOT show "Authentication failed"

### Test 2: SMS Sending (if test tasks available)
1. **Monitor sending**
   - If API returns SMS task, watch for:
   - ✅ "SMS Task - Queue: X, Phone: +381..."
   - ✅ "SMS sent: Queue X"
   - ✅ Statistics update (Sent count increases)

### Test 3: Reply Capture - PIN Confirmation

1. **Send PIN reply from another phone**
   ```
   English: "PIN: 1234"
   Serbian: "Moj PIN je 1234"
   Or just: "1234"
   ```

2. **Verify in app**
   - ✅ Reply Statistics > Total Replies increases
   - ✅ Reply Statistics > PIN Confirmations increases
   - ✅ Activity Log shows "[REPLY] Reply from +381..."
   - ✅ Activity Log shows "Reply queued: pin_confirmation"

3. **Check backend dashboard**
   - Visit: https://srb.trackify.net/api/sms/monitor.html
   - ✅ Should see reply in Recent Replies table
   - ✅ PIN verification attempt logged

### Test 4: Reply Capture - Opt-Out

1. **Send opt-out messages**
   ```
   English: "STOP" or "UNSUBSCRIBE"
   Serbian: "PREKINI" or "ODJAVI"
   ```

2. **Verify**
   - ✅ Reply classified as "opt_out"
   - ✅ Shows in Reply Statistics
   - ✅ Backend adds to opt-out list

### Test 5: Reply Capture - Inquiries

1. **Send inquiry messages**
   ```
   English: "When will my package arrive?"
   Serbian: "Kada stize paket?"
   ```

2. **Verify**
   - ✅ Reply classified as "general_inquiry"
   - ✅ Queued for manual review

### Test 6: Batch Processing

1. **Send multiple replies quickly**
   - Send 3-5 different SMS replies
   
2. **Wait 30 seconds**
   - ✅ Activity Log shows batch sent
   - ✅ "Successfully sent X replies to server"
   - ✅ Last Sync time updates

### Test 7: Background Operation

1. **Lock screen and wait**
   - Lock device screen
   - Wait 2 minutes
   - Unlock and check:
   - ✅ Service still running
   - ✅ Probes continued (check timestamps)
   - ✅ Replies still captured

2. **App in background**
   - Press Home button
   - Send test reply from another phone
   - Return to app
   - ✅ Reply was captured and queued

---

## 🔍 Monitoring Tools

### App Interface
- **Activity Log**: Real-time events with timestamps
- **SMS Statistics**: Sent/Pending/Failed counts
- **Reply Statistics**: 
  - Total Replies received
  - Queued for Server
  - PIN Confirmations
  - Last Sync timestamp

### Backend Dashboard
URL: https://srb.trackify.net/api/sms/monitor.html

Features:
- SMS Replies Overview (24-hour stats)
- PIN Verification Success Rate
- Opt-out Tracking
- Manual Review Queue
- Recent Replies Table
- PIN Verification Attempts Log
- Reply Type Distribution Chart

### Direct API Testing
```bash
# Check reply statistics
curl -X GET "https://srb.trackify.net/api/sms/get_reply_stats.php" \
  -H "X-API-Key: osafu2379jsaf"
```

---

## 🐛 Troubleshooting

### Issue: Replies not being captured
- Check RECEIVE_SMS permission granted
- Verify SmsReceiver registered in manifest
- Check if number is in recent recipients
- Look for "[REPLY]" entries in Activity Log

### Issue: Replies not sent to server
- Check internet connection
- Verify API key is correct
- Check "Queued for Server" count
- Wait 30 seconds for batch processing
- Check Last Sync timestamp

### Issue: PIN not verifying
- Ensure shipment_id exists in backend
- Check PIN format (4 digits)
- Verify within 24-hour window
- Check for 3-failure blocking

### Issue: Service stops
- Check battery optimization settings
- Grant "Ignore Battery Optimizations"
- Ensure notification permission granted
- Check if device killing background apps

---

## ✅ Success Criteria

The integration is successful when:

1. **Service Operations**
   - [ ] Runs continuously in background
   - [ ] Probes API at set intervals
   - [ ] Sends SMS when tasks available
   - [ ] Tracks delivery status

2. **Reply Handling**
   - [ ] Captures all incoming SMS
   - [ ] Correctly classifies reply types
   - [ ] Queues replies for server
   - [ ] Sends batches every 30 seconds
   - [ ] Updates statistics in real-time

3. **Backend Integration**
   - [ ] Authentication works (API key)
   - [ ] Status updates received by backend
   - [ ] Replies appear in dashboard
   - [ ] PIN verification works
   - [ ] Opt-outs processed

4. **Serbian Language Support**
   - [ ] Serbian keywords recognized
   - [ ] "PREKINI" triggers opt-out
   - [ ] "Kada" queries classified correctly
   - [ ] Serbian PIN messages work

---

## 📊 Performance Benchmarks

Expected performance:
- **Probe Response Time**: < 2 seconds
- **SMS Send Time**: < 5 seconds
- **Reply Capture**: Immediate
- **Reply Batch Send**: Every 30 seconds
- **Battery Usage**: < 5% per day
- **Memory Usage**: < 50MB
- **Network Usage**: < 10MB per day

---

## 🎯 Production Readiness

Before production deployment:
- [ ] Test with 100+ SMS per day
- [ ] Verify 24-hour continuous operation
- [ ] Test network disconnection recovery
- [ ] Verify all reply types classified
- [ ] Confirm backend dashboard working
- [ ] Test with various Android versions
- [ ] Document any device-specific issues

---

## 📝 Test Results Template

```
Test Date: ___________
Device: ___________
Android Version: ___________
Network: ___________

Service Start: [ ] Pass [ ] Fail
API Connection: [ ] Pass [ ] Fail
SMS Sending: [ ] Pass [ ] Fail
Reply Capture: [ ] Pass [ ] Fail
PIN Verification: [ ] Pass [ ] Fail
Opt-Out: [ ] Pass [ ] Fail
Batch Processing: [ ] Pass [ ] Fail
Background Operation: [ ] Pass [ ] Fail
Serbian Support: [ ] Pass [ ] Fail

Notes:
_____________________
_____________________
_____________________
```

---

## 📞 Support Contacts

**Android App Issues**: [Your Contact]
**Backend Issues**: info@etrg.net / 0659102040
**Dashboard**: https://srb.trackify.net/api/sms/monitor.html

---

*Last Updated: December 28, 2024*
*Backend Status: ✅ COMPLETE AND TESTED*