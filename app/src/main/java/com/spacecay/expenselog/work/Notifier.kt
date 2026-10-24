package com.spacecay.expenselog.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spacecay.expenselog.MainActivity
import com.spacecay.expenselog.R
import com.spacecay.expenselog.autolog.AutoLogger
import com.spacecay.expenselog.autolog.ParsedPayment
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.BudgetMath
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money

object Notifier {
    const val CH_LOG = "autolog"
    const val CH_BUDGET = "budget"
    const val CH_DAILY = "daily"
    const val CH_BILLS = "bills"

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LOG, "Auto-logged payments", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_DAILY, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BILLS, "Bill reminders", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun canPost(ctx: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(ctx: Context, route: String, req: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @Suppress("MissingPermission")
    private fun post(ctx: Context, id: Int, n: android.app.Notification) {
        if (!canPost(ctx)) return
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) {}
    }

    fun logged(ctx: Context, txId: Long, p: ParsedPayment) {
        val n = NotificationCompat.Builder(ctx, CH_LOG)
            .setSmallIcon(R.drawable.ic_add_white)
            .setContentTitle("Logged: " + AutoLogger.describe(p))
            .setContentText("Tap to check the category")
            .setContentIntent(open(ctx, "txedit?id=$txId&type=${p.type}", txId.toInt()))
            .setAutoCancel(true)
            .build()
        post(ctx, 1000 + (txId % 1000).toInt(), n)
    }

    suspend fun checkBudgets(ctx: Context, bookId: Long) {
        val dao = Graph.dao
        val list = dao.allBudgets().filter { it.bookId == bookId }
        if (list.isEmpty()) return
        val bd = BookData(bookId, dao.getBook(bookId), dao.getRates(bookId), dao.getWallets(bookId), dao.getCategories(bookId), emptyList())
        val sp = ctx.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)
        for (b in list) {
            val r = BudgetMath.range(b)
            val txns = dao.getTxnsBetween(bookId, r.start, r.end)
            val spent = BudgetMath.spent(b, txns, bd, r)
            if (b.amount <= 0) continue
            val pct = (spent * 100 / b.amount).toInt()
            val level = when { pct >= 100 -> 2; pct >= b.alertPercent -> 1; else -> 0 }
            val key = "b${b.id}_${r.start}"
            val last = sp.getInt(key, 0)
            if (level > last) {
                sp.edit().putInt(key, level).apply()
                val title = if (level == 2) "Budget exceeded: ${b.name}" else "${b.name}: $pct% used"
                val text = "Spent ${Money.format(spent, bd.main)} of ${Money.format(b.amount, bd.main)}"
                post(ctx, 2000 + b.id.toInt(), NotificationCompat.Builder(ctx, CH_BUDGET)
                    .setSmallIcon(R.drawable.ic_add_white).setContentTitle(title).setContentText(text)
                    .setContentIntent(open(ctx, "budget/${b.id}", 2000 + b.id.toInt())).setAutoCancel(true).build())
            }
        }
    }

    suspend fun dailySummary(ctx: Context) {
        val dao = Graph.dao
        val bookId = Graph.prefs.bookId.value
        val bd = BookData(bookId, dao.getBook(bookId), dao.getRates(bookId), dao.getWallets(bookId), dao.getCategories(bookId), dao.getWalletDeltas(bookId))
        val r = Dates.day(System.currentTimeMillis())
        val txns = dao.getTxnsBetween(bookId, r.start, r.end)
        val spent = Stats.expense(txns, bd)
        val got = Stats.income(txns, bd)
        val count = txns.count { Stats.counts(it) }
        val title = if (count == 0) "No spending logged today" else "Today: spent ${Money.format(spent, bd.main)}"
        val text = if (count == 0) "Anything to add? Tap to log it." else
            "$count transactions" + (if (got > 0) " · received ${Money.format(got, bd.main)}" else "") + " · tap for today's log"
        post(ctx, 3000, NotificationCompat.Builder(ctx, CH_DAILY)
            .setSmallIcon(R.drawable.ic_add_white).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx, "dailylog", 3000)).setAutoCancel(true).build())
    }

    fun balanceMismatch(ctx: Context, wallet: com.spacecay.expenselog.data.Wallet, reported: Long, inApp: Long) {
        val text = "${wallet.name} says ${Money.format(reported, wallet.currency)}, the app shows ${Money.format(inApp, wallet.currency)}. A payment may be missing — tap to check or adjust."
        post(ctx, 4000 + wallet.id.toInt(), NotificationCompat.Builder(ctx, CH_LOG)
            .setSmallIcon(R.drawable.ic_add_white).setContentTitle("${wallet.name} balance doesn't match")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx, "wallet/${wallet.id}", 4000 + wallet.id.toInt())).setAutoCancel(true).build())
    }

    /** "Rent is due" with Add / Skip buttons. */
    fun billDue(ctx: Context, r: com.spacecay.expenselog.data.Recurring, title: String, amountText: String) {
        val id = 5000 + r.id.toInt()
        fun action(act: String): PendingIntent = PendingIntent.getBroadcast(
            ctx, id * 10 + act.length,
            Intent(ctx, BillActionReceiver::class.java).setAction(act).putExtra("rid", r.id).putExtra("due", r.nextDate),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(ctx, id, NotificationCompat.Builder(ctx, CH_BILLS)
            .setSmallIcon(R.drawable.ic_add_white)
            .setContentTitle("Bill due: $title")
            .setContentText("$amountText · ${Dates.date(r.nextDate)}")
            .setContentIntent(open(ctx, "home", id))
            .addAction(0, "Add it", action(BillActionReceiver.ADD))
            .addAction(0, "Skip", action(BillActionReceiver.SKIP))
            .setAutoCancel(true).build())
    }

    fun cancel(ctx: Context, id: Int) { NotificationManagerCompat.from(ctx).cancel(id) }
}
