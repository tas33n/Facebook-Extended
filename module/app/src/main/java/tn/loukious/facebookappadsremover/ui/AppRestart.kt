package tn.loukious.facebookappadsremover.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import tn.loukious.facebookappadsremover.core.L

internal object AppRestart {
    fun restart(activity: Activity, prefs: SharedPreferences, failed: () -> Unit) {
        // commit queues behind prior apply writes. Do disk I/O off the main thread.
        Thread {
            val saved = runCatching { prefs.edit().putLong("panel.lastRestart", System.currentTimeMillis()).commit() }.getOrDefault(false)
            Handler(Looper.getMainLooper()).post {
                if (!saved || activity.isDestroyed || activity.isFinishing) {
                    failed()
                    Toast.makeText(activity, "Couldn't save settings. Please try again.", Toast.LENGTH_LONG).show()
                    return@post
                }
                runCatching {
                    activity.startActivity(Intent().setComponent(ComponentName(activity.packageName,
                        "tn.loukious.fbar.embedded.RestartActivity"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
                }.onFailure {
                    failed()
                    L.w("FBAR.Restart", "Restart helper could not open", it)
                    Toast.makeText(activity, "Please force-stop Facebook and reopen it.", Toast.LENGTH_LONG).show()
                }
            }
        }.apply { name = "FBAR-SaveBeforeRestart" }.start()
    }
}
