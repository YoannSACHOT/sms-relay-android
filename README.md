# SMS Relay

A minimal Android app (~130 lines of Java, 12 KB APK) that forwards every incoming SMS to a configurable phone number.

Useful for: receiving SMS from one of your phones (e.g. an old SIM you can't reach easily) on a device of your choice (e.g. another phone whose SMS you can read remotely).

## Why this exists

Existing SMS forwarder apps on Play Store either bundle telemetry, are massive (15+ MB), or forward to a webhook (not a phone number). This one does one thing, with no network access at all.

## Security model

- **Permissions requested**: `RECEIVE_SMS`, `READ_SMS`, `SEND_SMS`, `RECEIVE_BOOT_COMPLETED`. Nothing else.
- **No `INTERNET` permission**: the app cannot exfiltrate any data anywhere.
- **No third-party dependencies**: pure AOSP `SmsManager` + `BroadcastReceiver`. No analytics SDK, no crash reporter, no anything.
- **Receiver is protected by `android.permission.BROADCAST_SMS`**: only the system can deliver fake SMS to it.
- **Loop guard**: if the sender of an incoming SMS equals the configured target, the forward is skipped (prevents recursion).
- **Self-signed**: not on Play Store, distributed as APK only. Build it yourself, or trust a release.

## Build

Requirements: Android SDK with `build-tools/36.0.0` and `platforms/android-34` (or adjust paths in `build.sh`), Java 11+.

```bash
./build.sh
```

Produces `build/SmsRelay-signed.apk` (~12 KB).

The first run generates a debug keystore in `build/debug.keystore` (gitignored). Replace with your own for releases.

## Install & configure

```bash
adb install -r build/SmsRelay-signed.apk

# Grant SMS permissions
adb shell pm grant io.github.yoannsachot.smsrelay android.permission.RECEIVE_SMS
adb shell pm grant io.github.yoannsachot.smsrelay android.permission.READ_SMS
adb shell pm grant io.github.yoannsachot.smsrelay android.permission.SEND_SMS

# Set the forward target (E.164 format)
adb shell am start -n io.github.yoannsachot.smsrelay/.MainActivity --es set_target +33712345678

# Optional: send a test SMS to verify SmsManager works on this device
adb shell am start -n io.github.yoannsachot.smsrelay/.MainActivity --es test_send "hello"

# Optional: disable temporarily without uninstalling
adb shell am start -n io.github.yoannsachot.smsrelay/.MainActivity --es set_enabled false
```

## Forward format

When an SMS arrives, the app sends `[<sender>] <body>` (truncated to 140 chars with `...` suffix if longer) via `SmsManager.sendTextMessage` to the configured target.

The sender phone number is preserved in the prefix so you know who originally sent the message.

## Cost

Each forward costs whatever your carrier charges for one outgoing national SMS. In France with most plans that include unlimited SMS, the cost is 0 €/message.

## Caveats

- **RCS messages are not forwarded**. Only legacy SMS triggers `SMS_RECEIVED`. RCS chats (Google Messages bidirectional rich chats) are handled internally by Google Messages and don't fire the broadcast.
- **MMS are not forwarded**. This app only handles `Telephony.Sms.Intents.SMS_RECEIVED_ACTION`.
- **Messages longer than 140 chars** are truncated. Multi-part SMS reassembly works on input but not on output (the forward is a single SMS).
- **No filtering**: every SMS is forwarded. To skip e.g. promotional senders, fork and add a check in `SmsReceiver.onReceive()`.

## License

MIT - see `LICENSE`.
