package com.spacecay.expenselog.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest

/** Small key-value settings, with observable flows for the values the UI reacts to. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _bookId = MutableStateFlow(sp.getLong("bookId", 0L))
    val bookId: StateFlow<Long> = _bookId
    fun setBook(id: Long) { sp.edit().putLong("bookId", id).apply(); _bookId.value = id }

    /** 0 = follow system, 1 = light, 2 = dark */
    private val _theme = MutableStateFlow(sp.getInt("theme", 0))
    val theme: StateFlow<Int> = _theme
    fun setTheme(v: Int) { sp.edit().putInt("theme", v).apply(); _theme.value = v }

    private val _hideAmounts = MutableStateFlow(sp.getBoolean("hideAmounts", false))
    val hideAmounts: StateFlow<Boolean> = _hideAmounts
    fun setHideAmounts(v: Boolean) { sp.edit().putBoolean("hideAmounts", v).apply(); _hideAmounts.value = v }

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) = sp.edit().putBoolean("seeded", v).apply()

    // ----- Lock -----
    var pinHash: String?
        get() = sp.getString("pinHash", null)
        set(v) = sp.edit().putString("pinHash", v).apply()
    var biometric: Boolean
        get() = sp.getBoolean("biometric", false)
        set(v) = sp.edit().putBoolean("biometric", v).apply()
    val lockEnabled: Boolean get() = pinHash != null

    fun checkPin(pin: String) = pinHash == hash(pin)
    fun savePin(pin: String?) { pinHash = pin?.let { hash(it) } }
    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(("el$s").toByteArray()).joinToString("") { "%02x".format(it) }

    // ----- Calendar -----
    /** java.util.Calendar day constant: 1 = Sunday, 2 = Monday */
    var firstDayOfWeek: Int
        get() = sp.getInt("firstDay", 2)
        set(v) = sp.edit().putInt("firstDay", v).apply()

    /** Day of month the "month" starts (salary day budgets). 1..28 */
    var monthStartDay: Int
        get() = sp.getInt("monthStart", 1)
        set(v) = sp.edit().putInt("monthStart", v).apply()

    // ----- Auto-logging -----
    var autoLogNotifications: Boolean
        get() = sp.getBoolean("autoNotif", true)
        set(v) = sp.edit().putBoolean("autoNotif", v).apply()
    var autoLogScreenshots: Boolean
        get() = sp.getBoolean("autoShots", true)
        set(v) = sp.edit().putBoolean("autoShots", v).apply()
    var watchedPackages: Set<String>
        get() = sp.getStringSet("watchedPkgs", null) ?: DEFAULT_PACKAGES
        set(v) = sp.edit().putStringSet("watchedPkgs", v).apply()
    var seenPackages: Set<String>
        get() = sp.getStringSet("seenPkgs", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("seenPkgs", v).apply()
    var telebirrWalletId: Long
        get() = sp.getLong("telebirrWallet", 0L)
        set(v) = sp.edit().putLong("telebirrWallet", v).apply()
    var cbeWalletId: Long
        get() = sp.getLong("cbeWallet", 0L)
        set(v) = sp.edit().putLong("cbeWallet", v).apply()
    var lastScreenshotScan: Long
        get() = sp.getLong("lastShotScan", 0L)
        set(v) = sp.edit().putLong("lastShotScan", v).apply()
    /** Log the service fee as its own expense line. */
    var logFees: Boolean
        get() = sp.getBoolean("logFees", true)
        set(v) = sp.edit().putBoolean("logFees", v).apply()

    // ----- Daily summary -----
    var dailySummary: Boolean
        get() = sp.getBoolean("dailySummary", true)
        set(v) = sp.edit().putBoolean("dailySummary", v).apply()
    var dailySummaryMinutes: Int
        get() = sp.getInt("dailySummaryAt", 21 * 60)
        set(v) = sp.edit().putInt("dailySummaryAt", v).apply()

    // ----- Backup -----
    var backupFolder: String?
        get() = sp.getString("backupFolder", null)
        set(v) = sp.edit().putString("backupFolder", v).apply()
    /** 0 = off, 1 = daily, 2 = weekly */
    var autoBackup: Int
        get() = sp.getInt("autoBackup", 0)
        set(v) = sp.edit().putInt("autoBackup", v).apply()
    var lastBackup: Long
        get() = sp.getLong("lastBackup", 0L)
        set(v) = sp.edit().putLong("lastBackup", v).apply()

    companion object {
        val DEFAULT_PACKAGES = setOf(
            "cn.tydic.ethiopay",          // telebirr
            "et.ethiotelecom.telebirr",
            "com.ethiotelecom.telebirr",
            "com.combanketh.mobilebanking", // CBE Mobile
            "com.cbe.mobilebanking",
            "com.cbe.birr",
        )
        val SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
        )
    }
}
