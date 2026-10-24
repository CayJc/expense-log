package com.spacecay.expenselog.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.spacecay.expenselog.MainActivity
import com.spacecay.expenselog.R
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.BudgetMath
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import kotlinx.coroutines.launch

/** Bigger widget: today's spending, the busiest budget and the last 3 transactions. */
class RecentWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Graph.init(context)
        Graph.scope.launch { try { render(context, manager, ids) } finally { pending.finish() } }
    }

    companion object {
        fun requestUpdate(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, RecentWidget::class.java))
            if (ids.isEmpty()) return
            Graph.scope.launch { try { render(ctx, mgr, ids) } catch (_: Exception) {} }
        }

        private suspend fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val d = Graph.dao
            val bookId = Graph.prefs.bookId.value
            val bd = BookData(bookId, d.getBook(bookId), d.getRates(bookId), d.getWallets(bookId), d.getCategories(bookId), d.getWalletDeltas(bookId))
            val hidden = Graph.prefs.hideAmounts.value
            val today = Dates.day(System.currentTimeMillis())
            val spent = Stats.expense(d.getTxnsBetween(bookId, today.start, today.end), bd)
            val latest = d.latestTxns(bookId, 3)

            // Budget closest to its limit
            val budgets = d.allBudgets().filter { it.bookId == bookId && it.amount > 0 }
            val top = budgets.map { b ->
                val r = BudgetMath.range(b)
                b to BudgetMath.spent(b, d.getTxnsBetween(bookId, r.start, r.end), bd, r)
            }.maxByOrNull { (b, s) -> s.toDouble() / b.amount }

            val open = PendingIntent.getActivity(ctx, 30, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val add = PendingIntent.getActivity(ctx, 31, Intent(ctx, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_ROUTE, "txedit?id=0&type=0")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val rows = listOf(Triple(R.id.r1, R.id.r1_t, R.id.r1_a), Triple(R.id.r2, R.id.r2_t, R.id.r2_a), Triple(R.id.r3, R.id.r3_t, R.id.r3_a))
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_recent)
                v.setTextViewText(R.id.rw_today, if (hidden) "••••" else Money.format(spent, bd.main))
                if (top != null) {
                    val (b, s) = top
                    val pct = (s * 100 / b.amount).toInt()
                    v.setTextViewText(R.id.rw_budget, "${b.name}: " + (if (hidden) "$pct%" else "${Money.compact(s)} / ${Money.compact(b.amount)} ($pct%)"))
                    v.setProgressBar(R.id.rw_bar, 100, pct.coerceIn(0, 100), false)
                    v.setViewVisibility(R.id.rw_budget, View.VISIBLE); v.setViewVisibility(R.id.rw_bar, View.VISIBLE)
                } else {
                    v.setViewVisibility(R.id.rw_budget, View.GONE); v.setViewVisibility(R.id.rw_bar, View.GONE)
                }
                rows.forEachIndexed { i, (row, t, a) ->
                    val tx = latest.getOrNull(i)
                    if (tx == null) { v.setViewVisibility(row, View.GONE); return@forEachIndexed }
                    v.setViewVisibility(row, View.VISIBLE)
                    val disp = bd.display(tx)
                    v.setTextViewText(t, "${Dates.fmt("d MMM", tx.dateTime)} · ${disp.title}" + (if (tx.payee.isNotBlank()) " · ${tx.payee}" else ""))
                    val signed = if (tx.type == TxType.EXPENSE) -tx.amount else tx.amount
                    v.setTextViewText(a, if (hidden) "••••" else Money.format(signed, bd.currencyOf(tx.walletId), tx.type == TxType.INCOME))
                    v.setTextColor(a, when (tx.type) { TxType.INCOME -> 0xFF2E9E6A.toInt(); TxType.EXPENSE -> 0xFFE5484D.toInt(); else -> 0xFF6B7AA1.toInt() })
                }
                v.setOnClickPendingIntent(R.id.rw_root, open)
                v.setOnClickPendingIntent(R.id.rw_add, add)
                mgr.updateAppWidget(id, v)
            }
        }
    }
}
