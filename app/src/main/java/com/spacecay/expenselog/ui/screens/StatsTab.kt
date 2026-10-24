package com.spacecay.expenselog.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.data.Txn
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.BarChart
import com.spacecay.expenselog.ui.components.BarPoint
import com.spacecay.expenselog.ui.components.CategoryIcon
import com.spacecay.expenselog.ui.components.DonutChart
import com.spacecay.expenselog.ui.components.DonutSlice
import com.spacecay.expenselog.ui.components.EmptyState
import com.spacecay.expenselog.ui.components.PeriodSwitcher
import com.spacecay.expenselog.ui.components.Pill
import com.spacecay.expenselog.ui.components.ProgressBar
import com.spacecay.expenselog.ui.components.SectionTitle
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.util.PeriodKind
import com.spacecay.expenselog.util.Range
import java.util.Calendar
import kotlin.math.abs

@Composable
fun StatsTab(bd: BookData, hidden: Boolean, go: (String) -> Unit) {
    val m = LocalMoney.current
    var anchor by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var kindIdx by rememberSaveable { mutableIntStateOf(PeriodKind.MONTH.ordinal) }
    var typeSel by rememberSaveable { mutableIntStateOf(TxType.EXPENSE) }
    val kind = PeriodKind.entries[kindIdx]
    val range = Dates.range(kind, anchor)
    val prevRange = Dates.range(kind, Dates.shift(kind, anchor, -1))
    val txns by remember(bd.bookId, range) { Graph.dao.observeTxnsBetween(bd.bookId, range.start, range.end) }.collectAsStateWithLifecycle(emptyList())
    val prev by remember(bd.bookId, prevRange) { Graph.dao.observeTxnsBetween(bd.bookId, prevRange.start, prevRange.end) }.collectAsStateWithLifecycle(emptyList())

    val slices = remember(txns, typeSel, bd) { Stats.byCategory(txns, bd, typeSel) }
    val total = slices.sumOf { it.amount }
    val income = Stats.income(txns, bd)
    val expense = Stats.expense(txns, bd)
    val prevTotal = if (typeSel == TxType.EXPENSE) Stats.expense(prev, bd) else Stats.income(prev, bd)
    val accent = if (typeSel == TxType.EXPENSE) m.expense else m.income

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PeriodKind.WEEK to "Week", PeriodKind.MONTH to "Month", PeriodKind.YEAR to "Year", PeriodKind.ALL to "All").forEach { (k, l) ->
                    Pill(l, kind == k, { kindIdx = k.ordinal })
                }
            }
        }
        if (kind != PeriodKind.ALL) item {
            PeriodSwitcher(Dates.label(kind, anchor), { anchor = Dates.shift(kind, anchor, -1) }, { anchor = Dates.shift(kind, anchor, 1) }, Modifier.padding(horizontal = 8.dp))
        }
        item {
            AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row {
                    SmallStat("Income", if (hidden) "••••" else Money.format(income, bd.main), m.income, Modifier.weight(1f))
                    SmallStat("Expense", if (hidden) "••••" else Money.format(expense, bd.main), m.expense, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Expenses", typeSel == TxType.EXPENSE, { typeSel = TxType.EXPENSE }, color = m.expense)
                Pill("Income", typeSel == TxType.INCOME, { typeSel = TxType.INCOME }, color = m.income)
            }
        }
        if (slices.isEmpty()) {
            item { EmptyState(Icons.Filled.PieChart, "No data for this period", "Add some transactions to see where your money goes.") }
            return@LazyColumn
        }
        item {
            AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    DonutChart(
                        slices.map { DonutSlice(it.amount.toFloat(), it.color) },
                        centerTop = if (typeSel == TxType.EXPENSE) "Spent" else "Received",
                        centerBottom = if (hidden) "••••" else Money.compact(total),
                    )
                    if (kind != PeriodKind.ALL && prevTotal > 0) {
                        val change = (total - prevTotal) * 100.0 / prevTotal
                        val up = change >= 0
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${if (up) "▲" else "▼"} ${"%.0f".format(abs(change))}% vs previous ${kind.name.lowercase()}",
                            color = if (up == (typeSel == TxType.EXPENSE)) m.expense else m.income,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        item { SectionTitle("By category", Modifier.padding(horizontal = 16.dp)) }
        item {
            AppCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                Column {
                    slices.forEach { s ->
                        val pct = if (total > 0) s.amount * 100f / total else 0f
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (s.key != null) Modifier.clickableNoRipple { go("cattx/${s.key}?from=${range.start}&to=${range.end}") } else Modifier)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(s.icon, s.color, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(if (hidden) "••••" else Money.format(s.amount, bd.main), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { ProgressBar(pct / 100f, s.color, height = 6.dp) }
                                    Spacer(Modifier.width(10.dp))
                                    Text("%.1f%%".format(pct), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("Trend", Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        item {
            val points = remember(txns, kind, range, bd) { trendPoints(txns, bd, kind, range) }
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend("Income", m.income); Legend("Expense", m.expense)
                    }
                    Spacer(Modifier.height(10.dp))
                    BarChart(points, m.income, m.expense, showB = true, labelEvery = when { points.size > 20 -> 5; points.size > 12 -> 2; else -> 1 })
                }
            }
        }
        item {
            val days = when (kind) {
                PeriodKind.ALL -> maxOf(1, Dates.daysBetween(txns.minOfOrNull { it.dateTime } ?: range.start, System.currentTimeMillis()) + 1)
                else -> {
                    val end = minOf(range.end, System.currentTimeMillis())
                    maxOf(1, Dates.daysBetween(range.start, end) + 1)
                }
            }
            val biggest = txns.filter { Stats.counts(it) && it.type == typeSel }.maxByOrNull { bd.mainAmount(it) }
            AppCard(Modifier.padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row {
                        SmallStat("Daily average", if (hidden) "••••" else Money.format(total / days, bd.main), accent, Modifier.weight(1f))
                        SmallStat("Transactions", txns.count { Stats.counts(it) && it.type == typeSel }.toString(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    }
                    if (biggest != null) {
                        SmallStat("Largest", (if (hidden) "••••" else Money.format(bd.mainAmount(biggest), bd.main)) + " · " + bd.display(biggest).title,
                            MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(10.dp).then(Modifier.padding(0.dp))) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawCircle(color) }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

fun trendPoints(txns: List<Txn>, bd: BookData, kind: PeriodKind, range: Range): List<BarPoint> {
    val counted = txns.filter { Stats.counts(it) }
    fun bucket(list: List<Txn>, label: String) = BarPoint(
        label,
        list.filter { it.type == TxType.INCOME }.sumOf { bd.mainAmount(it) },
        list.filter { it.type == TxType.EXPENSE }.sumOf { bd.mainAmount(it) },
    )
    return when (kind) {
        PeriodKind.YEAR -> (0 until 12).map { mo ->
            val c = Dates.cal(range.start); c.add(Calendar.MONTH, mo)
            val r = Dates.calendarMonth(c.timeInMillis)
            bucket(counted.filter { it.dateTime in r }, Dates.fmt("MMM", c.timeInMillis).take(1))
        }
        PeriodKind.ALL -> {
            if (counted.isEmpty()) emptyList() else {
                val start = Dates.calendarMonth(counted.minOf { it.dateTime }).start
                val months = mutableListOf<BarPoint>()
                var t = start
                var guard = 0
                while (t < System.currentTimeMillis() && guard++ < 120) {
                    val r = Dates.calendarMonth(t)
                    months += bucket(counted.filter { it.dateTime in r }, Dates.fmt("MMM", t).take(3))
                    t = r.end
                }
                months.takeLast(12)
            }
        }
        else -> {
            val days = Dates.daysBetween(range.start, range.end)
            (0 until days).map { d ->
                val c = Dates.cal(range.start); c.add(Calendar.DAY_OF_MONTH, d)
                val r = Dates.day(c.timeInMillis)
                bucket(counted.filter { it.dateTime in r }, if (kind == PeriodKind.WEEK) Dates.fmt("EEE", c.timeInMillis).take(2) else Dates.fmt("d", c.timeInMillis))
            }
        }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
