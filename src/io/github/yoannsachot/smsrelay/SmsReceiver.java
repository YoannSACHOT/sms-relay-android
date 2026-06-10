package io.github.yoannsachot.smsrelay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsRelay";
    private static final int MAX_LEN = 140;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        SharedPreferences p = context.getSharedPreferences("smsrelay", Context.MODE_PRIVATE);
        String target = p.getString("target", "");
        if (target.isEmpty()) {
            Log.w(TAG, "No target configured, skipping. Set via MainActivity intent.");
            return;
        }
        if (!p.getBoolean("enabled", true)) {
            Log.i(TAG, "Disabled, skip");
            return;
        }

        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;

        StringBuilder body = new StringBuilder();
        String from = msgs[0].getOriginatingAddress();
        for (SmsMessage m : msgs) body.append(m.getMessageBody());

        if (target.equals(from)) {
            Log.w(TAG, "Skip: sender == target (loop guard)");
            return;
        }

        String prefix = "[" + (from == null ? "?" : from) + "] ";
        String full = prefix + body.toString();
        if (full.length() > MAX_LEN) full = full.substring(0, MAX_LEN - 3) + "...";

        try {
            SmsManager sm = context.getSystemService(SmsManager.class);
            if (sm == null) sm = SmsManager.getDefault();
            sm.sendTextMessage(target, null, full, null, null);
            // Ne pas logger le contenu du SMS (peut contenir des codes 2FA).
            Log.i(TAG, "Forwarded to target (" + full.length() + " chars)");
        } catch (Exception e) {
            Log.e(TAG, "Forward failed: " + e.getMessage());
        }
    }
}
