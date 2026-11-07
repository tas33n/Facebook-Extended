package tn.loukious.facebookappadsremover.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import tn.loukious.facebookappadsremover.core.L
import tn.loukious.facebookappadsremover.core.Settings
import tn.loukious.facebookappadsremover.core.SessionBackup
import java.util.WeakHashMap

/** Host-window UI: platform widgets only, no module resources or overlay permission. */
object EmbeddedPanel : Application.ActivityLifecycleCallbacks {
    private const val TAG = "FBAR.EmbeddedPanel"
    private var installed = false
    private lateinit var prefs: SharedPreferences
    private val entries = WeakHashMap<Activity, ProfileMenuEntry>()
    private val previews = WeakHashMap<Activity, ProfilePhotoPreview>()
    private val downloads = WeakHashMap<Activity, VideoDownloadEntry>()
    private var dialog: AlertDialog? = null
    private var owner: Activity? = null

    fun install(app: Application, preferences: SharedPreferences) {
        if (installed) return
        prefs = preferences
        installed = true
        app.registerActivityLifecycleCallbacks(this)
        L.i(TAG, "FBAR_PROFILE_PANEL_V3 registered")
    }

    override fun onActivityResumed(activity: Activity) {
        runCatching {
            if (activity.isFinishing || activity.isDestroyed) return
            previews.getOrPut(activity) { ProfilePhotoPreview(activity) }.start()
            downloads.getOrPut(activity) { VideoDownloadEntry(activity) }.start()
            entries.getOrPut(activity) { ProfileMenuEntry(activity) { dark ->
                runCatching { show(activity, dark) }.onFailure { L.w(TAG, "Panel opening failed", it) }
            } }.start()
        }.onFailure { L.w(TAG, "Panel attachment failed", it) }
    }

    private fun show(activity: Activity, dark: Boolean) {
        if (activity.isFinishing || activity.isDestroyed || dialog?.isShowing == true) return
        val context = ContextThemeWrapper(activity, if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
        val primary = if (dark) Color.rgb(228, 230, 235) else Color.rgb(28, 30, 33)
        val secondary = if (dark) Color.rgb(176, 179, 184) else Color.rgb(101, 103, 107)
        val surface = if (dark) Color.rgb(36, 37, 38) else Color.WHITE
        val card = if (dark) Color.rgb(48, 49, 51) else Color.rgb(240, 242, 245)
        val accent = if (dark) Color.rgb(117, 182, 255) else Color.rgb(8, 102, 255)
        fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
            setColor(color); cornerRadius = dp(context, radius).toFloat()
        }
        val shell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surface, 24)
            clipToOutline = true
            setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 12))
        }
        shell.addView(TextView(context).apply {
            text = "Extended"; textSize = 22f; setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(context, 6))
        })
        shell.addView(TextView(context).apply {
            text = "Make Facebook feel like yours."; textSize = 13f; setTextColor(secondary)
            setPadding(0, 0, 0, dp(context, 16))
        })
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(context, 12))
        }
        fun text(value: String, size: Float = 14f) = TextView(context).apply {
            this.text = value
            textSize = size
            setTextColor(secondary)
            setPadding(0, dp(context, 10), 0, dp(context, 6))
            body.addView(this)
        }
        text("Restart recommended\nChanges save automatically. After changing settings, use Restart app so startup-only features and background processes reload too.", 13f).apply {
            background = rounded(card, 14)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
        }
        for (section in TOGGLE_SECTIONS) {
            if (section.extra == SectionExtra.LAUNCHER) continue
            text(section.title, 16f).apply {
                setTextColor(accent); setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(context, 22), 0, dp(context, 8))
            }
            for (spec in section.toggles) {
                val toggle = Switch(context).apply {
                    text = spec.title
                    textSize = 15f
                    setTextColor(primary)
                    setPadding(0, dp(context, 8), 0, dp(context, 8))
                    isChecked = prefs.getBoolean(spec.key, spec.default)
                    thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, secondary))
                    trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf((accent and 0x00ffffff) or 0x66000000, if (dark) 0xff606368.toInt() else 0xffccd0d5.toInt()))
                    setOnCheckedChangeListener { _, value ->
                        prefs.edit().putBoolean(spec.key, value).apply()
                    }
                }
                body.addView(toggle)
                text(spec.subtitle, 12f).setPadding(0, 0, dp(context, 8), dp(context, 12))
                body.addView(View(context).apply { setBackgroundColor(card) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)))
            }
            if (section.extra == SectionExtra.KEYWORDS) {
                val input = EditText(context).apply {
                    hint = "Keywords separated by commas, semicolons or new lines"
                    setText(prefs.getString(Settings.FEED_KEYWORDS, ""))
                    minLines = 2
                    maxLines = 4
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: android.text.Editable?) {
                            prefs.edit().putString(Settings.FEED_KEYWORDS, s?.toString().orEmpty()).apply()
                        }
                    })
                }
                body.addView(input)
            }
            if (section.extra == SectionExtra.SESSION) {
                text("Session exports contain login data. Stored locally in Download/FacebookAppAdsRemover.", 12f)
                for ((label, action) in listOf("Export session" to SessionBackup.ACTION_EXPORT,
                    "Import latest session" to SessionBackup.ACTION_IMPORT)) {
                    body.addView(Button(context).apply {
                        this.text = label
                        setOnClickListener {
                            if (action == SessionBackup.ACTION_IMPORT) {
                                AlertDialog.Builder(context).setTitle(label)
                                    .setMessage("Restore the latest local export? Fully close and reopen Facebook afterwards.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Restore") { _, _ -> SessionBackup.runEmbedded(activity.applicationContext, action) }
                                    .show()
                            } else SessionBackup.runEmbedded(activity.applicationContext, action)
                        }
                    })
                }
            }
        }
        val scroll = ScrollView(context).apply { addView(body) }
        shell.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 12), 0, 0)
        }
        val restart = Button(context).apply {
            text = "Restart app"; isAllCaps = false; textSize = 14f
            setTextColor(accent); background = rounded(card, 12)
            contentDescription = "Save settings and restart Facebook"
        }
        val done = Button(context).apply {
            text = "Done"; isAllCaps = false; textSize = 14f
            setTextColor(if (dark) Color.rgb(20, 30, 45) else Color.WHITE)
            background = rounded(accent, 12)
            setOnClickListener { dialog?.dismiss() }
        }
        footer.addView(restart, LinearLayout.LayoutParams(0, dp(context, 48), 1f))
        footer.addView(View(context), LinearLayout.LayoutParams(dp(context, 10), 1))
        footer.addView(done, LinearLayout.LayoutParams(0, dp(context, 48), 1f))
        shell.addView(footer)
        val credits = TextView(context).apply {
            text = "Developed by @tas33n\ntas33n/Facebook-Extended · Built on Loukious’s FBAR"
            textSize = 11f; gravity = Gravity.CENTER; setTextColor(secondary)
            setPadding(0, dp(context, 12), 0, dp(context, 2))
            contentDescription = "Extended credits and GitHub links"
            setOnClickListener {
                AlertDialog.Builder(context).setTitle("Credits").setItems(arrayOf(
                    "@tas33n — Extended", "Loukious — Facebook App Ads Remover", "JingMatrix — LSPatch / Vector"
                )) { _, index ->
                    val urls = arrayOf("https://github.com/tas33n/Facebook-Extended",
                        "https://github.com/Xposed-Modules-Repo/tn.loukious.facebookappadsremover",
                        "https://github.com/JingMatrix/LSPatch")
                    runCatching { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(urls[index]))) }
                }.setNegativeButton("Close", null).show()
            }
        }
        shell.addView(credits)
        restart.setOnClickListener {
            restart.isEnabled = false; done.isEnabled = false; restart.text = "Restarting…"
            AppRestart.restart(activity, prefs) {
                restart.isEnabled = true; done.isEnabled = true; restart.text = "Restart app"
            }
        }
        owner = activity
        dialog = AlertDialog.Builder(context).create().also { window ->
                window.setView(shell, 0, 0, 0, 0)
                window.setOnDismissListener { dialog = null; owner = null }
                window.show()
                window.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window.window?.setLayout((activity.resources.displayMetrics.widthPixels - dp(context, 32)).coerceAtMost(dp(context, 480)),
                    (activity.resources.displayMetrics.heightPixels * 0.8f).toInt())
            }
    }

    private fun detach(activity: Activity) {
        runCatching {
            if (owner === activity) dialog?.dismiss()
            entries.remove(activity)?.stop()
            previews.remove(activity)?.stop()
            downloads.remove(activity)?.stop()
        }.onFailure { L.w(TAG, "Panel cleanup failed", it) }
    }

    override fun onActivityPaused(activity: Activity) = detach(activity)
    override fun onActivityDestroyed(activity: Activity) = detach(activity)
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
