@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.spacecay.expenselog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.Budget
import com.spacecay.expenselog.data.BudgetPeriod
import com.spacecay.expenselog.data.CatType
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.BudgetMath
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.ColorPicker
import com.spacecay.expenselog.ui.components.ConfirmDialog
import com.spacecay.expenselog.ui.components.DateDialog
import com.spacecay.expenselog.ui.components.DonutChart
import com.spacecay.expenselog.ui.components.DonutSlice
import com.spacecay.expenselog.ui.components.EmptyState
import com.spacecay.expenselog.ui.components.MoneyField
import com.spacecay.expenselog.ui.components.PeriodSwitcher
import com.spacecay.expenselog.ui.components.PickerField
import com.spacecay.expenselog.ui.components.Pill
import com.spacecay.expenselog.ui.components.ProgressBar
import com.spacecay.expenselog.ui.components.SectionTitle
import com.spacecay.expenselog.ui.components.SimpleTopBar
import com.spacecay.expenselog.ui.components.SolidIcon
import com.spacecay.expenselog.ui.components.dayGroupedTxns
import com.spacecay.expenselog.ui.rememberBookData
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.AppIcons
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.util.Palette
import kotlinx.coroutines.launch

@Composable
fun BudgetsScreen(back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val budgets by remember(bd.bookId) { Graph.dao.observeBudgets(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    val ranges = budgets.map { BudgetMath.range(it) }
    val from = ranges.minOfOrNull { it.start } ?: 0L
    val to = ranges.maxOfOrNull { it.end } ?: 0L
    val txns by remember(bd.bookId, from, to) { Graph.dao.observeTxnsBetween(bd.bookId, from, to) }.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Budgets", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { go("budgetedit?id=0") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { Icon(Icons.Filled.Add, "Add") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (budgets.isEmpty()) item { EmptyState(Icons.Filled.PieChart, "No budgets yet", "Set a spending limit for a category or for everything, per week, month or year.") }
            if (budgets.isNotEmpty()) item {
                val totalBudget = budgets.filter { it.period == BudgetPeriod.MONTHLY }.sumOf { it.amount }
                val totalSpent = budgets.filter { it.period == BudgetPeriod.MONTHLY }.sumOf { BudgetMath.spent(it, txns, bd) }
                if (totalBudget > 0) AppCard {
                    Column {
                        Text("Monthly budgets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (hidden) "••••" else "${Money.format(totalSpent, bd.main)} of ${Money.format(totalBudget, bd.main)}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(totalSpent.toFloat() / totalBudget, if (totalSpent > totalBudget) LocalMoney.current.expense else MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(budgets, key = { it.id }) { b -> BudgetCard(b, bd, txns, hidden) { go("budget/${b.id}") } }
        }
    }
}

@Composable
fun BudgetCard(b: Budget, bd: BookData, txns: List<com.spacecay.expenselog.data.Txn>, hidden: Boolean, onClick: () -> Unit) {
    val m = LocalMoney.current
    val r = BudgetMath.range(b)
    val spent = BudgetMath.spent(b, txns, bd, r)
    val left = b.amount - spent
    val frac = if (b.amount > 0) spent.toFloat() / b.amount else 0f
    val days = BudgetMath.daysLeft(r)
    val barColor = when {
        frac >= 1f -> m.expense
        frac * 100 >= b.alertPercent -> Color(0xFFFFA726)
        else -> Color(b.color)
    }
    val cats = BudgetMath.ids(b.categoryIds).mapNotNull { bd.catMap[it] }
    AppCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SolidIcon(cats.firstOrNull()?.let { AppIcons.get(it.icon) } ?: Icons.Filled.PieChart, Color(b.color), 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        (if (cats.isEmpty()) "All expenses" else cats.joinToString { it.name }) + " · " + BudgetPeriod.labels[b.period],
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = barColor)
            }
            Spacer(Modifier.height(10.dp))
            ProgressBar(frac, barColor, height = 10.dp)
            Spacer(Modifier.height(8.dp))
            Row {
                Text(if (hidden) "••••" else "${Money.format(spent, bd.main)} / ${Money.format(b.amount, bd.main)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    if (left >= 0) (if (hidden) "" else Money.format(left, bd.main) + " left") else (if (hidden) "Over" else "Over by " + Money.format(-left, bd.main)),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = if (left >= 0) m.income else m.expense,
                )
            }
            if (left > 0 && days > 0 && !hidden) {
                Text("$days days left · about ${Money.format(left / days, bd.main)} per day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BudgetDetailScreen(id: Long, back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val m = LocalMoney.current
    val scope = rememberCoroutineScope()
    val budgets by remember(bd.bookId) { Graph.dao.observeBudgets(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val b = budgets.firstOrNull { it.id == id }
    var anchor by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirm by remember { mutableStateOf(false) }
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    val r = b?.let { BudgetMath.range(it, anchor) }
    val txns by remember(bd.bookId, r) { Graph.dao.observeTxnsBetween(bd.bookId, r?.start ?: 0L, r?.end ?: 0L) }.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar(b?.name ?: "Budget", back) {
                IconButton(onClick = { go("budgetedit?id=$id") }) { Icon(Icons.Filled.Edit, "Edit") }
                IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        },
    ) { pad ->
        if (b == null || r == null) return@Scaffold
        val mine = txns.filter { BudgetMath.matches(b, it, bd) }
        val spent = BudgetMath.spent(b, txns, bd, r)
        val left = b.amount - spent
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            val k = BudgetMath.kind(b)
            if (k != null) item {
                PeriodSwitcher(Dates.label(k, anchor), { anchor = Dates.shift(k, anchor, -1) }, { anchor = Dates.shift(k, anchor, 1) }, Modifier.padding(horizontal = 8.dp))
            }
            item {
                AppCard(Modifier.padding(16.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val color = if (left < 0) m.expense else Color(b.color)
                        DonutChart(
                            listOf(DonutSlice(spent.coerceAtMost(b.amount).toFloat(), color), DonutSlice((b.amount - spent).coerceAtLeast(0).toFloat(), Color.Transparent)),
                            centerTop = if (left >= 0) "Left" else "Over", centerBottom = if (hidden) "••••" else Money.compact(kotlin.math.abs(left)), size = 180.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row {
                            SmallStat("Budget", if (hidden) "••••" else Money.format(b.amount, bd.main), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                            SmallStat("Spent", if (hidden) "••••" else Money.format(spent, bd.main), m.expense, Modifier.weight(1f))
                        }
                        val days = BudgetMath.daysLeft(r)
                        if (left > 0 && days > 0 && anchor >= r.start && System.currentTimeMillis() in r) {
                            Spacer(Modifier.height(8.dp))
                            Text("Safe to spend ${if (hidden) "••" else Money.format(left / days, bd.main)} per day for $days days",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            dayGroupedTxns(mine, bd, hidden) { go("tx/${it.id}") }
        }
    }
    if (confirm) ConfirmDialog("Delete budget?", "Transactions are not affected.", onDismiss = { confirm = false }) {
        scope.launch { Graph.dao.deleteBudget(id); back() }
    }
}

@Composable
fun BudgetEditScreen(id: Long, back: () -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    var orig by remember { mutableStateOf<Budget?>(null) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var period by remember { mutableIntStateOf(BudgetPeriod.MONTHLY) }
    var start by remember { mutableLongStateOf(Dates.startOfDay(System.currentTimeMillis())) }
    var end by remember { mutableLongStateOf(Dates.month(System.currentTimeMillis()).end - 1) }
    val cats = remember { mutableStateListOf<Long>() }
    val wallets = remember { mutableStateListOf<Long>() }
    var color by remember { mutableIntStateOf(Palette.at(4)) }
    var alert by remember { mutableIntStateOf(80) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) Graph.dao.getBudget(id)?.let { b ->
            orig = b; name = b.name; amount = Money.toInput(b.amount); period = b.period; start = b.startDate
            b.endDate?.let { end = it }
            cats.addAll(BudgetMath.ids(b.categoryIds)); wallets.addAll(BudgetMath.ids(b.walletIds)); color = b.color; alert = b.alertPercent
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { SimpleTopBar(if (id == 0L) "New budget" else "Edit budget", back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Budget name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            MoneyField(amount, { amount = it }, "Limit (${bd.main})", bd.main)
            SectionTitle("Period")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetPeriod.labels.forEachIndexed { i, l -> Pill(l, period == i, { period = i }) }
            }
            if (period == BudgetPeriod.ONCE) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField("From", Dates.date(start), Icons.Filled.CalendarToday, onClick = { pickStart = true }, modifier = Modifier.weight(1f))
                    PickerField("To", Dates.date(end), Icons.Filled.CalendarToday, onClick = { pickEnd = true }, modifier = Modifier.weight(1f))
                }
            }
            SectionTitle("Categories (none selected = all expenses)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                bd.categories.filter { it.type == CatType.EXPENSE && it.parentId == null }.forEach { c ->
                    FilterChip(
                        selected = c.id in cats,
                        onClick = { if (c.id in cats) cats.remove(c.id) else cats.add(c.id) },
                        label = { Text(c.name) },
                        leadingIcon = { Icon(AppIcons.get(c.icon), null, tint = Color(c.color), modifier = Modifier.width(18.dp)) },
                    )
                }
            }
            SectionTitle("Wallets (none selected = all)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bd.wallets.filter { !it.archived }.forEach { w ->
                    FilterChip(selected = w.id in wallets, onClick = { if (w.id in wallets) wallets.remove(w.id) else wallets.add(w.id) }, label = { Text(w.name) })
                }
            }
            SectionTitle("Warn me at $alert%")
            Slider(alert.toFloat(), { alert = it.toInt() }, valueRange = 50f..100f, steps = 9)
            SectionTitle("Colour")
            ColorPicker(color) { color = it }
            Button(
                onClick = {
                    val amt = Money.parse(amount) ?: 0L
                    if (amt <= 0) return@Button
                    val n = name.trim().ifBlank {
                        if (cats.isEmpty()) "Total spending" else cats.mapNotNull { bd.catMap[it]?.name }.joinToString(", ")
                    }
                    val b = (orig ?: Budget(bookId = bd.bookId, name = n, amount = amt, startDate = start, color = color)).copy(
                        name = n, amount = amt, period = period, startDate = start, endDate = if (period == BudgetPeriod.ONCE) end else null,
                        categoryIds = cats.joinToString(","), walletIds = wallets.joinToString(","), color = color, alertPercent = alert,
                    )
                    scope.launch { if (b.id == 0L) Graph.dao.insertBudget(b) else Graph.dao.updateBudget(b); back() }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Save") }
        }
    }
    if (pickStart) DateDialog(start, { pickStart = false }) { start = it }
    if (pickEnd) DateDialog(end, { pickEnd = false }) { end = Dates.day(it).end - 1 }
}
