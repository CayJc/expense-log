package com.spacecay.expenselog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.EmptyState
import com.spacecay.expenselog.ui.components.PeriodSwitcher
import com.spacecay.expenselog.ui.components.TxRow
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import java.util.Calendar

@Composable
fun CalendarTab(bd: BookData, hidden: Boolean, go: (String) -> Unit) {
    val m = LocalMoney.current
    var anchor by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var selected by rememberSaveable { mutableLongStateOf(Dates.startOfDay(System.currentTimeMillis())) }
    val range = Dates.calendarMonth(anchor)
    val txns by remember(bd.bookId, range) { Graph.dao.observeTxnsBetween(bd.bookId, range.start, range.end) }.collectAsStateWithLifecycle(emptyList())
    val byDay = remember(txns) { txns.groupBy { Dates.startOfDay(it.dateTime) } }
    val today = Dates.startOfDay(System.currentTimeMillis())
    val eth = Graph.prefs.ethCal.collectAsState().value == 1

    // Build grid cells
    val first = Dates.cal(range.start)
    val lead = ((first.get(Calendar.DAY_OF_WEEK) - Dates.firstDayOfWeek) + 7) % 7
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells: List<Long?> = List(lead) { null } + (0 until daysInMonth).map { d ->
        Dates.cal(range.start).apply { add(Calendar.DAY_OF_MONTH, d) }.timeInMillis
    }
    val weekLabels = (0 until 7).map { i ->
        val c = Calendar.getInstance(); c.set(Calendar.DAY_OF_WEEK, ((Dates.firstDayOfWeek - 1 + i) % 7) + 1)
        Dates.fmt("EEE", c.timeInMillis).take(2)
    }
    val dayList = byDay[selected].orEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            PeriodSwitcher(Dates.fmt("MMMM yyyy", anchor) + (if (eth) "\n" + com.spacecay.expenselog.util.EthiopianCalendar.monthLabel(range.start) + " – " + com.spacecay.expenselog.util.EthiopianCalendar.monthLabel(range.end - 1) else ""), onPrev = { anchor = Dates.shift(com.spacecay.expenselog.util.PeriodKind.MONTH, range.start, -1) },
                onNext = { anchor = Dates.shift(com.spacecay.expenselog.util.PeriodKind.MONTH, range.start, 1) }, modifier = Modifier.padding(horizontal = 8.dp))
        }
        item {
            AppCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(10.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        weekLabels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Spacer(Modifier.height(6.dp))
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                Box(Modifier.weight(1f).aspectRatio(0.78f).padding(2.dp)) {
                                    if (day != null) {
                                        val list = byDay[day].orEmpty()
                                        val inc = Stats.income(list, bd)
                                        val exp = Stats.expense(list, bd)
                                        val isSel = day == selected
                                        Column(
                                            Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                                .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                                .then(if (day == today && !isSel) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                                                .clickable { selected = day }
                                                .padding(vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(Dates.fmt("d", day), style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (day == today) FontWeight.Bold else FontWeight.Medium)
                                            if (eth) Text(com.spacecay.expenselog.util.EthiopianCalendar.of(day).day.toString(), fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.weight(1f))
                                            if (inc > 0 && !hidden) Text(Money.compact(inc), fontSize = 9.sp, color = m.income, maxLines = 1)
                                            if (exp > 0 && !hidden) Text(Money.compact(exp), fontSize = 9.sp, color = m.expense, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Dates.dayHeader(selected), style = MaterialTheme.typography.titleMedium)
                    Text(Dates.fmt("EEEE, d MMMM yyyy", selected) + (if (eth) " · " + com.spacecay.expenselog.util.EthiopianCalendar.format(selected) else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                    val inc = Stats.income(dayList, bd); val exp = Stats.expense(dayList, bd)
                    if (!hidden) {
                        Text("+" + Money.format(inc, bd.main), color = m.income, style = MaterialTheme.typography.labelLarge)
                        Text("-" + Money.format(exp, bd.main), color = m.expense, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (dayList.isEmpty()) item { EmptyState(Icons.Filled.EventBusy, "Nothing on this day", "Tap + to add a transaction.") }
        else item {
            AppCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(4.dp)) {
                Column { dayList.forEach { t -> TxRow(t, bd, hidden) { go("tx/${t.id}") } } }
            }
        }
        item {
            val mi = Stats.income(txns, bd); val me = Stats.expense(txns, bd)
            AppCard(Modifier.padding(16.dp)) {
                Row {
                    SmallStat("Month income", if (hidden) "••••" else Money.format(mi, bd.main), m.income, Modifier.weight(1f))
                    SmallStat("Month expense", if (hidden) "••••" else Money.format(me, bd.main), m.expense, Modifier.weight(1f))
                }
            }
        }
    }
}
