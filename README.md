# SMS Sender Android App

An Android application that automatically probes a URL endpoint and sends SMS messages based on the JSON response.

## Features

- Automatic URL probing every 60 seconds
- SMS sending based on JSON response
- Response callback after successful SMS delivery
- Real-time status updates and logging
- Start/Stop control for probing

## Requirements

- Android 5.0 (API level 21) or higher
- SMS permission

## Setup

1. Clone this repository
2. Open the project in Android Studio
3. Build and run on your Android device
4. Grant SMS permission when prompted

## Usage

1. Launch the app
2. Tap "Start Probing" to begin automatic URL probing
3. The app will check the URL every 60 seconds for SMS instructions
4. View real-time logs in the app interface
5. Tap "Stop Probing" to stop the automatic process

## API Format

The app expects a JSON response from `https://eds-ks.com/api/sms/prober.php` in this format:

```json
{
  "phone": "+1234567890",
  "message": "Your SMS message here",
  "shipment_id": "12345"
}
```

After sending the SMS, the app sends a POST request back with:
- `shipment_id={shipment_id}`
- `sms_sent='{sms_message}'`
- `response=success`

## Permissions

The app requires the following permissions:
- `SEND_SMS` - To send SMS messages
- `INTERNET` - To make HTTP requests
- `ACCESS_NETWORK_STATE` - To check network connectivity

## Building

To build the APK:
1. Open in Android Studio
2. Select Build > Build Bundle(s) / APK(s) > Build APK(s)
3. The APK will be generated in `app/build/outputs/apk/`

## License

This project is available for use as needed.