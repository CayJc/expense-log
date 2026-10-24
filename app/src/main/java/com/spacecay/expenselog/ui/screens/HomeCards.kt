package com.spacecay.expenselog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.CategoryIcon
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.AppIcons
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

/** "Bills due" with Add / Skip right on the home screen. */
@Composable
fun BillsDueCard(bd: BookData, hidden: Boolean) {
    val scope = rememberCoroutineScope()
    val now = remember { System.currentTimeMillis() }
    val bills by remember(bd.bookId) { Graph.dao.observeDueBills(now) }.collectAsStateWithLifecycle(emptyList())
    val mine = bills.filter { it.bookId == bd.bookId }
    if (mine.isEmpty()) return
    AppCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EventRepeat, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Bills due", style = MaterialTheme.typography.titleSmall)
            }
            mine.forEach { r ->
                val c = r.categoryId?.let { bd.catMap[it] }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c != null) { CategoryIcon(AppIcons.get(c.icon), androidx.compose.ui.graphics.Color(c.color), 34.dp); Spacer(Modifier.width(10.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(r.note.ifBlank { c?.name ?: "Bill" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text((if (hidden) "••••" else Money.format(r.amount, bd.currencyOf(r.walletId))) + " · due " + Dates.date(r.nextDate),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { scope.launch { Graph.repo.advanceBill(r) } }) { Text("Skip") }
                    FilledTonalButton(onClick = { scope.launch { Graph.repo.payBill(r) } }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Add") }
                }
            }
        }
    }
}

/** A few plain-language observations about this month's spending. */
@Composable
fun InsightsCard(bd: BookData, hidden: Boolean) {
    val m = LocalMoney.current
    val now = remember { System.currentTimeMillis() }
    val from = remember(now) { minOf(Dates.month(now).start, Dates.week(Dates.shift(com.spacecay.expenselog.util.PeriodKind.WEEK, now, -1)).start) }
    val txns by remember(bd.bookId, from) { Graph.dao.observeTxnsBetween(bd.bookId, from, Long.MAX_VALUE) }.collectAsStateWithLifecycle(emptyList())
    val lines = remember(txns, bd, hidden) { buildInsights(txns, bd, now, hidden) }
    if (lines.isEmpty()) return
    AppCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Insights, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Insights", style = MaterialTheme.typography.titleSmall)
            }
            lines.forEach { (good, text) ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Lightbulb, null, tint = if (good == null) MaterialTheme.colorScheme.onSurfaceVariant else if (good) m.income else m.expense,
                        modifier = Modifier.padding(top = 2.dp).width(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun buildInsights(all: List<com.spacecay.expenselog.data.Txn>, bd: BookData, now: Long, hidden: Boolean): List<Pair<Boolean?, String>> {
    val out = mutableListOf<Pair<Boolean?, String>>()
    fun money(v: Long) = if (hidden) "••••" else Money.format(v, bd.main)

    // Week vs last week (same number of days so far)
    val week = Dates.week(now)
    val lastWeekStart = Dates.week(Dates.shift(com.spacecay.expenselog.util.PeriodKind.WEEK, now, -1)).start
    val elapsed = now - week.start
    val thisWeek = Stats.expense(all.filter { it.dateTime in week.start..now }, bd)
    val lastWeek = Stats.expense(all.filter { it.dateTime >= lastWeekStart && it.dateTime < lastWeekStart + elapsed }, bd)
    if (lastWeek > 0 && thisWeek > 0) {
        val pct = ((thisWeek - lastWeek) * 100 / lastWeek).toInt()
        if (abs(pct) >= 10) out += (pct < 0) to "You've spent ${abs(pct)}% ${if (pct > 0) "more" else "less"} this week than at this point last week (${money(thisWeek)} vs ${money(lastWeek)})."
    }

    // Top category this month
    val month = Dates.month(now)
    val monthTx = all.filter { it.dateTime in month.start until month.end }
    val slices = Stats.byCategory(monthTx, bd, TxType.EXPENSE)
    val total = slices.sumOf { it.amount }
    slices.firstOrNull()?.let { top ->
        if (total > 0) out += null to "${top.name} is your biggest expense this month: ${money(top.amount)} (${top.amount * 100 / total}% of spending)."
    }

    // Projection for the month
    val days = Dates.daysBetween(month.start, month.end - 1) + 1
    val done = (Dates.daysBetween(month.start, now) + 1).coerceIn(1, days)
    if (done in 5 until days && total > 0) {
        val projected = total * days / done
        val income = Stats.income(monthTx, bd)
        out += (if (income > 0) projected <= income else null) to
            "At this pace you'll spend about ${money(projected)} this month" + (if (income > 0) " (income so far ${money(income)})." else ".")
    }

    // Biggest single spend this week
    all.filter { Stats.counts(it) && it.type == TxType.EXPENSE && it.dateTime in week.start..now }
        .maxByOrNull { bd.mainAmount(it) }?.let { t ->
            if (bd.mainAmount(t) * 4 > thisWeek && thisWeek > 0) out += null to "Biggest spend this week: ${money(bd.mainAmount(t))} on ${bd.display(t).title}${if (t.payee.isNotBlank()) " (${t.payee})" else ""}."
        }

    // Late-night spending habit
    val late = monthTx.filter { Stats.counts(it) && it.type == TxType.EXPENSE && Dates.cal(it.dateTime).get(Calendar.HOUR_OF_DAY) >= 22 }
    if (late.size >= 5) out += false to "${late.size} purchases after 10 PM this month, ${money(late.sumOf { bd.mainAmount(it) })} in total."

    return out.take(4)
}
