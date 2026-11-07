package tn.loukious.fbar.embedded;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.widget.TextView;

/** Runs in a separate same-UID process so relaunch survives termination of Facebook. */
public final class RestartActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView status = new TextView(this);
        status.setText("Restarting Facebook…");
        status.setGravity(android.view.Gravity.CENTER);
        status.setTextSize(18);
        setContentView(status);
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch == null) { status.setText("Close Facebook and open it again from your launcher."); return; }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            java.util.List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes != null) for (ActivityManager.RunningAppProcessInfo p : processes) {
                if (p.uid == Process.myUid() && p.pid != Process.myPid() &&
                    (p.processName.equals(getPackageName()) || p.processName.startsWith(getPackageName() + ":"))) {
                    Process.killProcess(p.pid);
                }
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { startActivity(launch); finishAndRemoveTask(); }
                catch (RuntimeException e) { status.setText("Facebook was closed. Open it again from your launcher."); }
            }, 450);
        }, 250);
    }
}
