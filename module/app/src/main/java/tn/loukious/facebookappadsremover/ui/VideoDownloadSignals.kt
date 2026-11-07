package tn.loukious.facebookappadsremover.ui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/** Coalesced player events; listeners are removed with their Activity lifecycle. */
object VideoDownloadSignals {
    private val main = Handler(Looper.getMainLooper())
    private val pending = AtomicBoolean()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    fun add(listener: () -> Unit) { listeners.add(listener) }
    fun remove(listener: () -> Unit) { listeners.remove(listener) }
    fun notifyChanged() {
        if (!pending.compareAndSet(false, true)) return
        main.post { pending.set(false); listeners.forEach { it() } }
    }
}
