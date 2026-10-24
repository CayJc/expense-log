package com.spacecay.expenselog.autolog

import android.app.Notification
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.Prefs
import com.spacecay.expenselog.data.TxSource
import kotlinx.coroutines.launch

/**
 * Reads payment notifications from telebirr / CBE (and any app the user picks) and logs them.
 * While connected it also watches for new screenshots so receipts are logged within seconds.
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val recent = ArrayDeque<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null
    private val scan = Runnable {
        Graph.scope.launch { try { ScreenshotScanner.scan(applicationContext) } catch (_: Exception) {} }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Graph.init(applicationContext)
        if (observer == null) {
            val obs = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    handler.removeCallbacks(scan)
                    handler.postDelayed(scan, 2500)
                }
            }
            try {
                contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs)
                observer = obs
            } catch (_: Exception) {
            }
        }
        handler.postDelayed(scan, 1000)
    }

    override fun onListenerDisconnected() {
        observer?.let { try { contentResolver.unregisterContentObserver(it) } catch (_: Exception) {} }
        observer = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try { handle(sbn ?: return) } catch (_: Exception) {}
    }

    private fun isPaymentApp(pkg: String): Boolean {
        if (pkg in Prefs.DEFAULT_PACKAGES) return true
        val p = pkg.lowercase()
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString().lowercase()
        } catch (_: Exception) { "" }
        return "telebirr" in p || "telebirr" in label || "cbe" in p || "combanketh" in p || "cbe" in label || "commercial bank" in label
    }

    private fun handle(n: StatusBarNotification) {
        val pkg = n.packageName ?: return
        if (pkg == packageName) return
        Graph.init(applicationContext)
        val prefs = Graph.prefs

        // Remember which apps post notifications so the user can choose them in settings.
        val seen = prefs.seenPackages
        if (pkg !in seen) prefs.seenPackages = (seen + pkg).toList().takeLast(40).toSet()

        if (!prefs.autoLogNotifications) return
        val paymentApp = isPaymentApp(pkg)
        if (pkg !in prefs.watchedPackages && !paymentApp) return

        val notif = n.notification ?: return
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = notif.extras ?: return
        val parts = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" ") { it.toString() },
        )
        val text = parts.joinToString(" ").trim()
        if (text.isEmpty()) return

        // Same notification often gets re-posted (updates); skip exact repeats.
        val key = pkg + text.hashCode()
        if (key in recent) return
        recent.addLast(key); if (recent.size > 30) recent.removeFirst()

        val parsed = PaymentParser.parse(text, pkg, trusted = paymentApp) ?: return
        Graph.scope.launch {
            try {
                Graph.repo.ensureSeeded()
                AutoLogger.log(applicationContext, parsed, TxSource.NOTIFICATION)
            } catch (_: Exception) {
            }
        }
    }
}
