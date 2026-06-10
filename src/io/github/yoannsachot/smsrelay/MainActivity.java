package io.github.yoannsachot.smsrelay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "SmsRelay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences p = getSharedPreferences("smsrelay", MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit();

        Intent in = getIntent();
        // L'activité est exportée (LAUNCHER) : n'importe quelle app locale peut donc
        // l'ouvrir avec des extras. On ne traite la reconfiguration (cible du relais,
        // envoi de SMS) que si l'appelant est de confiance (ADB shell, system, root,
        // ou l'app elle-même). Sinon un malware local pourrait rediriger les SMS 2FA.
        boolean trusted = isTrustedConfigCaller();
        boolean hasConfigExtras = in.hasExtra("set_target")
            || in.hasExtra("set_enabled") || in.hasExtra("test_send");
        if (hasConfigExtras && !trusted) {
            Log.w(TAG, "Ignored config intent from untrusted caller");
        }

        if (trusted) {
            String setTarget = in.getStringExtra("set_target");
            if (setTarget != null) {
                e.putString("target", setTarget);
                Log.i(TAG, "Target updated");
            }
            String setEnabled = in.getStringExtra("set_enabled");
            if (setEnabled != null) {
                e.putBoolean("enabled", "true".equalsIgnoreCase(setEnabled));
                Log.i(TAG, "Enabled updated: " + setEnabled);
            }
            e.apply();
        }

        String testText = trusted ? in.getStringExtra("test_send") : null;
        String testResult = "";
        if (testText != null) {
            String target = p.getString("target", "");
            if (target.isEmpty()) {
                testResult = "TEST FAILED: no target configured";
            } else {
                try {
                    SmsManager sm = getSystemService(SmsManager.class);
                    if (sm == null) sm = SmsManager.getDefault();
                    sm.sendTextMessage(target, null, testText, null, null);
                    testResult = "TEST SENT to " + target + ": " + testText;
                } catch (Exception ex) {
                    testResult = "TEST FAILED: " + ex.getMessage();
                }
            }
            Log.i(TAG, testResult);
        }

        String target = p.getString("target", "(not set)");
        boolean enabled = p.getBoolean("enabled", true);

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(50, 100, 50, 50);
        TextView t = new TextView(this);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        t.setText("SMS Relay\n\nForward target: " + target +
            "\nEnabled: " + enabled + "\n\n" +
            "Configure via ADB:\n" +
            "  adb shell am start -n io.github.yoannsachot.smsrelay/.MainActivity \\\n" +
            "    --es set_target +33XXXXXXXXX\n\n" +
            "Toggle:\n" +
            "  --es set_enabled true|false\n\n" +
            "Test send:\n" +
            "  --es test_send \"hello\"\n\n" +
            (testResult.isEmpty() ? "Standby." : testResult));
        l.addView(t);
        setContentView(l);
        finish();
    }

    /**
     * N'autorise la reconfiguration du relais que pour un appelant de confiance.
     * getLaunchedFromUid() est disponible depuis Android 14 (API 34). En deçà,
     * on ne peut pas identifier l'appelant de façon fiable : on refuse alors toute
     * configuration distante par sécurité (la config locale via ADB reste possible
     * sur les versions récentes, cible réelle de cet outil).
     */
    private boolean isTrustedConfigCaller() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int uid = getLaunchedFromUid();
            return uid == 2000          // ADB shell
                || uid == 1000          // system
                || uid == 0             // root
                || uid == Process.myUid();
        }
        return false;
    }
}
