package tn.loukious.facebookappadsremover.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import tn.loukious.facebookappadsremover.core.L

/** An activity-owned action anchored to the mounted profile action row.
 * Litho owns its child ordering: do not insert unmanaged children into its mount tree.
 * Bounds come from real/virtual actions, never fixed screen coordinates.
 */
internal class ProfileMenuEntry(private val activity: Activity, private val open: (Boolean) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var button: SlidersButton? = null
    private var panelDark = false
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            runCatching { update() }.onFailure {
                button?.visibility = View.GONE
                L.w("FBAR.ProfileEntry", "Profile row inspection failed", it)
            }
            if (active) handler.postDelayed(this, 450)
        }
    }

    fun start() { if (!active) { active = true; handler.post(tick) } }
    fun stop() {
        active = false
        handler.removeCallbacks(tick)
        button?.let { (it.parent as? ViewGroup)?.removeView(it) }
        button = null
    }

    private data class Action(val label: String, val bounds: Rect, val host: View)
    private fun update() {
        val root = activity.window.decorView as? FrameLayout ?: return
        if (activity.isFinishing || activity.isDestroyed || !root.hasWindowFocus()) return
        val actions = ArrayList<Action>()
        val pending = java.util.ArrayDeque<View>()
        pending.add(root)
        var visited = 0
        var virtualBudget = 120
        while (pending.isNotEmpty() && visited++ < 1200) {
            val view = pending.removeFirst()
            if (view === button || !view.isShown || view.width == 0 || view.height == 0) continue
            val rect = Rect()
            if (!view.getGlobalVisibleRect(rect)) continue
            val label = view.contentDescription?.toString().orEmpty()
            if (isAction(label)) actions.add(Action(label, rect, view))
            // Read virtual accessibility nodes only from mounted views, outside layout callbacks.
            if (virtualBudget > 0 && rect.top < root.height / 2) {
                val provider = view.accessibilityNodeProvider
                if (provider != null) {
                    val nodes = java.util.ArrayDeque<AccessibilityNodeInfo>()
                    provider.createAccessibilityNodeInfo(View.NO_ID)?.let(nodes::add)
                    val seen = HashSet<Int>()
                    while (nodes.isNotEmpty() && virtualBudget-- > 0) {
                        val node = nodes.removeFirst()
                        val text = node.contentDescription?.toString().orEmpty()
                        val bounds = Rect(); node.getBoundsInScreen(bounds)
                        if (isAction(text) && node.isVisibleToUser) actions.add(Action(text, bounds, view))
                        for (i in 0 until node.childCount.coerceAtMost(30)) {
                            // Android packs the virtual descendant in the HIGH 32 bits.
                            val id = runCatching { childId?.invoke(node, i) as? Long }.getOrNull()?.ushr(32)?.toInt()
                            if (id != null && seen.add(id)) provider.createAccessibilityNodeInfo(id)?.let(nodes::add)
                        }
                        @Suppress("DEPRECATION") node.recycle()
                    }
                    while (nodes.isNotEmpty()) { @Suppress("DEPRECATION") nodes.removeFirst().recycle() }
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) pending.add(view.getChildAt(i))
        }
        val more = actions.firstOrNull { it.label == "More profile settings" }
        val edit = actions.firstOrNull { it.label == "Edit profile" }
        val search = actions.firstOrNull { it.label == "Search" && more != null &&
            kotlin.math.abs(it.bounds.centerY() - more.bounds.centerY()) < more.bounds.height() / 2 }
        if (more == null || edit == null || search == null ||
            kotlin.math.abs(edit.bounds.centerY() - more.bounds.centerY()) > more.bounds.height() / 2) {
            button?.visibility = View.GONE
            return
        }
        val size = edit.bounds.height()
        val rtl = root.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val x = if (rtl) edit.bounds.right else edit.bounds.left - size
        val menu = actions.firstOrNull { it.label == "Menu" }
        if (size <= 0 || x < 0 || x + size > root.width ||
            (menu != null && Rect.intersects(Rect(x, edit.bounds.top, x + size, edit.bounds.bottom), menu.bounds))) {
            button?.visibility = View.GONE
            return
        }
        val isDark = darkTheme(root)
        panelDark = isDark
        val color = iconColor(edit.host, isDark)
        val entry = button ?: SlidersButton(activity).also {
            it.contentDescription = "Extension settings"
            it.tooltipText = "Extension settings"
            it.setOnClickListener { open(panelDark) }
            it.elevation = 0f
            root.addView(it, FrameLayout.LayoutParams(size, size))
            button = it
            L.i("FBAR.ProfileEntry", "FBAR_PROFILE_ACTION_V2 attached")
        }
        val location = IntArray(2); root.getLocationOnScreen(location)
        if (entry.layoutParams.width != size || entry.layoutParams.height != size) {
            entry.layoutParams = FrameLayout.LayoutParams(size, size)
        }
        entry.x = (x - location[0]).toFloat()
        entry.y = (edit.bounds.top - location[1]).toFloat()
        entry.ink = color
        entry.visibility = View.VISIBLE
        entry.bringToFront()
    }

    private fun isAction(s: String) = s in setOf("Edit profile", "More profile settings", "Search", "Menu")
    private fun darkTheme(root: View): Boolean {
        // Facebook's own background can differ from the system's theme.
        var current: View? = root.findViewById(android.R.id.content)
        repeat(5) {
            val c = (current?.background as? ColorDrawable)?.color
            if (c != null && Color.alpha(c) > 240) return luminance(c) < 0.4
            current = (current as? ViewGroup)?.let { if (it.childCount > 0) it.getChildAt(0) else null }
        }
        return activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    private fun iconColor(host: View, dark: Boolean): Int {
        if (host is ImageView) host.imageTintList?.let { return it.getColorForState(host.drawableState, it.defaultColor) }
        var view: View? = host
        repeat(3) {
            val c = (view?.background as? ColorDrawable)?.color
            if (c != null && Color.alpha(c) > 240) return if (luminance(c) < 0.4) Color.WHITE else Color.rgb(28, 30, 33)
            view = view?.parent as? View
        }
        // Profile's transparent cover-photo action row uses white line icons.
        return Color.WHITE
    }
    private fun luminance(c: Int) = (Color.red(c) * 0.2126 + Color.green(c) * 0.7152 + Color.blue(c) * 0.0722) / 255
    private class SlidersButton(activity: Activity) : View(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        var ink = Color.WHITE
            set(value) { if (field != value) { field = value; invalidate() } }
        init {
            isClickable = true; isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(0x33909090), null, ColorDrawable(Color.WHITE))
        }
        override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val side = width.coerceAtMost(height) * 0.60f
            canvas.save(); canvas.translate((width - side) / 2, (height - side) / 2); canvas.scale(side / 24, side / 24)
            paint.color = ink; paint.strokeWidth = 1.7f
            for ((y, knob) in listOf(5f to 8f, 12f to 16f, 19f to 10f)) {
                canvas.drawLine(3f, y, knob - 2.5f, y, paint)
                canvas.drawLine(knob + 2.5f, y, 21f, y, paint)
                canvas.drawCircle(knob, y, 2.5f, paint)
            }
            canvas.restore()
        }
    }
    companion object {
        private val childId = runCatching {
            AccessibilityNodeInfo::class.java.getDeclaredMethod("getChildId", Int::class.javaPrimitiveType).apply { isAccessible = true }
        }.getOrNull()
    }
}
