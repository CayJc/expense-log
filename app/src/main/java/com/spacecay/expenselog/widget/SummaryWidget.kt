package com.spacecay.expenselog.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.spacecay.expenselog.MainActivity
import com.spacecay.expenselog.R
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import kotlinx.coroutines.launch

class SummaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Graph.init(context)
        Graph.scope.launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        fun requestUpdate(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SummaryWidget::class.java))
            if (ids.isEmpty()) return
            Graph.scope.launch { try { render(ctx, mgr, ids) } catch (_: Exception) {} }
        }

        private suspend fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val d = Graph.dao
            val bookId = Graph.prefs.bookId.value
            val bd = BookData(bookId, d.getBook(bookId), d.getRates(bookId), d.getWallets(bookId), d.getCategories(bookId), d.getWalletDeltas(bookId))
            val now = System.currentTimeMillis()
            val today = Dates.day(now)
            val month = Dates.month(now)
            val monthTx = d.getTxnsBetween(bookId, month.start, month.end)
            val todaySpent = Stats.expense(monthTx.filter { it.dateTime in today }, bd)
            val monthSpent = Stats.expense(monthTx, bd)
            val hidden = Graph.prefs.hideAmounts.value

            val open = PendingIntent.getActivity(ctx, 10, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val add = PendingIntent.getActivity(ctx, 11, Intent(ctx, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_ROUTE, "txedit?id=0&type=0")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_summary)
                v.setTextViewText(R.id.w_today, if (hidden) "••••" else Money.format(todaySpent, bd.main))
                v.setTextViewText(R.id.w_month, "This month: " + if (hidden) "••••" else Money.format(monthSpent, bd.main))
                v.setTextViewText(R.id.w_balance, "Balance: " + if (hidden) "••••" else Money.format(bd.total, bd.main))
                v.setOnClickPendingIntent(R.id.widget_root, open)
                v.setOnClickPendingIntent(R.id.w_add, add)
                mgr.updateAppWidget(id, v)
            }
        }
    }
}
