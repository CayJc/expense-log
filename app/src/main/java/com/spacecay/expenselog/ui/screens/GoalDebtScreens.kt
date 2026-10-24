@file:OptIn(ExperimentalMaterial3Api::class)

package com.spacecay.expenselog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.Debt
import com.spacecay.expenselog.data.DebtType
import com.spacecay.expenselog.data.Goal
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.ColorPicker
import com.spacecay.expenselog.ui.components.ConfirmDialog
import com.spacecay.expenselog.ui.components.CurrencyPickerSheet
import com.spacecay.expenselog.ui.components.DateDialog
import com.spacecay.expenselog.ui.components.DonutChart
import com.spacecay.expenselog.ui.components.DonutSlice
import com.spacecay.expenselog.ui.components.EmptyState
import com.spacecay.expenselog.ui.components.IconGrid
import com.spacecay.expenselog.ui.components.MoneyField
import com.spacecay.expenselog.ui.components.PickerField
import com.spacecay.expenselog.ui.components.ProgressBar
import com.spacecay.expenselog.ui.components.SectionTitle
import com.spacecay.expenselog.ui.components.SimpleTopBar
import com.spacecay.expenselog.ui.components.SolidIcon
import com.spacecay.expenselog.ui.components.WalletPickerSheet
import com.spacecay.expenselog.ui.rememberBookData
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.AppIcons
import com.spacecay.expenselog.util.Currencies
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.util.Palette
import kotlinx.coroutines.launch

// ======================= GOALS =======================

@Composable
fun GoalsScreen(back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val goals by remember(bd.bookId) { Graph.dao.observeGoals(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val entries by remember { Graph.dao.observeAllGoalEntries() }.collectAsStateWithLifecycle(emptyList())
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Saving goals", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { go("goaledit?id=0") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { Icon(Icons.Filled.Add, "Add") }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Ongoing") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Reached") })
            }
            val list = goals.filter { it.reached == (tab == 1) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (list.isEmpty()) item { EmptyState(Icons.Filled.Flag, if (tab == 0) "No goals yet" else "Nothing reached yet", "Save towards something — a phone, a trip, an emergency fund.") }
                items(list, key = { it.id }) { g ->
                    val saved = entries.filter { it.goalId == g.id }.sumOf { it.amount }
                    val frac = if (g.target > 0) saved.toFloat() / g.target else 0f
                    AppCard(onClick = { go("goal/${g.id}") }) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SolidIcon(AppIcons.get(g.icon), Color(g.color), 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(g.name, style = MaterialTheme.typography.titleSmall)
                                    Text(g.deadline?.let { "By " + Dates.date(it) } ?: "No deadline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = Color(g.color))
                            }
                            Spacer(Modifier.height(10.dp))
                            ProgressBar(frac, Color(g.color), height = 10.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(if (hidden) "••••" else "${Money.format(saved, g.currency)} of ${Money.format(g.target, g.currency)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoalDetailScreen(id: Long, back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    val goals by remember(bd.bookId) { Graph.dao.observeGoals(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val entries by remember(id) { Graph.dao.observeGoalEntries(id) }.collectAsStateWithLifecycle(emptyList())
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    val g = goals.firstOrNull { it.id == id }
    var entryDialog by remember { mutableIntStateOf(0) } // 1 deposit, -1 withdraw
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar(g?.name ?: "Goal", back) {
                IconButton(onClick = { go("goaledit?id=$id") }) { Icon(Icons.Filled.Edit, "Edit") }
                IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        },
    ) { pad ->
        if (g == null) return@Scaffold
        val saved = entries.sumOf { it.amount }
        val left = (g.target - saved).coerceAtLeast(0)
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        DonutChart(listOf(DonutSlice(saved.coerceAtMost(g.target).toFloat(), Color(g.color)), DonutSlice(left.toFloat(), Color.Transparent)),
                            "Saved", if (hidden) "••••" else Money.compact(saved), size = 180.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(if (hidden) "••••" else "Target ${Money.format(g.target, g.currency)}", style = MaterialTheme.typography.titleSmall)
                        if (left > 0) {
                            Text(if (hidden) "" else "${Money.format(left, g.currency)} to go", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            g.deadline?.let { dl ->
                                val days = Dates.daysBetween(System.currentTimeMillis(), dl)
                                if (days > 0 && !hidden) {
                                    val months = maxOf(1, days / 30)
                                    Text("$days days left · save ${Money.format(left / months, g.currency)} a month", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else Text("Goal reached! 🎉", color = LocalMoney.current.income, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { entryDialog = 1 }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Deposit") }
                            OutlinedButton(onClick = { entryDialog = -1 }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Remove, null); Spacer(Modifier.width(4.dp)); Text("Withdraw") }
                        }
                    }
                }
            }
            if (g.note.isNotBlank()) item { AppCard { Text(g.note) } }
            item { SectionTitle("History") }
            items(entries, key = { it.id }) { e ->
                var del by remember { mutableStateOf(false) }
                AppCard(padding = PaddingValues(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (hidden) "••••" else Money.format(e.amount, g.currency, true), fontWeight = FontWeight.SemiBold,
                                color = if (e.amount >= 0) LocalMoney.current.income else LocalMoney.current.expense)
                            Text(Dates.date(e.date) + (e.walletId?.let { " · " + (bd.walletMap[it]?.name ?: "") } ?: "") + (if (e.note.isNotBlank()) " · ${e.note}" else ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { del = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                }
                if (del) ConfirmDialog("Delete entry?", "The linked wallet transaction is removed too.", onDismiss = { del = false }) {
                    scope.launch { Graph.repo.deleteGoalEntry(e) }
                }
            }
        }
        if (entryDialog != 0) EntryDialog(
            bd, title = if (entryDialog > 0) "Add to ${g.name}" else "Withdraw from ${g.name}", currency = g.currency,
            walletHint = if (entryDialog > 0) "Take money from wallet" else "Put money into wallet",
            onDismiss = { entryDialog = 0 },
        ) { amt, date, note, wallet ->
            val sign = entryDialog
            scope.launch { Graph.repo.addGoalEntry(g, amt * sign, date, note, wallet) }
            entryDialog = 0
        }
    }
    if (confirm && g != null) {
        var removeTx by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete goal?") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(removeTx, { removeTx = it }); Text("Also delete linked wallet transactions")
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { Graph.repo.deleteGoal(g, removeTx); back() }; confirm = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** Amount + date + note + optional wallet. */
@Composable
fun EntryDialog(
    bd: BookData, title: String, currency: String, walletHint: String,
    onDismiss: () -> Unit, onSave: (Long, Long, String, Long?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var pickWallet by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val w = walletId?.let { bd.walletMap[it] }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField(amount, { amount = it }, "Amount", currency)
                PickerField("Date", Dates.date(date), Icons.Filled.CalendarToday, onClick = { pickDate = true })
                PickerField(walletHint, w?.name ?: "None — don't touch wallets", w?.let { AppIcons.get(it.icon) } ?: Icons.Filled.AccountBalanceWallet,
                    w?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant, onClick = { pickWallet = true })
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Money.parse(amount) ?: 0L
                if (a > 0) onSave(a, Dates.combine(date, Dates.cal().get(java.util.Calendar.HOUR_OF_DAY), Dates.cal().get(java.util.Calendar.MINUTE)), note.trim(), walletId)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickWallet) WalletPickerSheet(bd, walletId, allowNone = true, onDismiss = { pickWallet = false }) { walletId = it?.id }
    if (pickDate) DateDialog(date, { pickDate = false }) { date = it }
}

@Composable
fun GoalEditScreen(id: Long, back: () -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    var orig by remember { mutableStateOf<Goal?>(null) }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf<Long?>(null) }
    var color by remember { mutableIntStateOf(Palette.at(8)) }
    var icon by remember { mutableStateOf("savings") }
    var note by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id != 0L) Graph.dao.getGoal(id)?.let { g ->
            orig = g; name = g.name; target = Money.toInput(g.target); currency = g.currency; deadline = g.deadline
            color = g.color; icon = g.icon; note = g.note
        }
    }
    LaunchedEffect(bd.book) { if (currency.isEmpty() && bd.book != null) currency = bd.main }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { SimpleTopBar(if (id == 0L) "New goal" else "Edit goal", back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SolidIcon(AppIcons.get(icon), Color(color), 56.dp)
                Spacer(Modifier.width(14.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Goal name") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            MoneyField(target, { target = it }, "Target amount", currency)
            PickerField("Currency", "$currency · ${Currencies.info(currency).name}", null, onClick = { pickCurrency = true })
            Row(verticalAlignment = Alignment.CenterVertically) {
                PickerField("Deadline", deadline?.let { Dates.date(it) } ?: "None", Icons.Filled.Event, onClick = { pickDate = true }, modifier = Modifier.weight(1f))
                if (deadline != null) TextButton(onClick = { deadline = null }) { Text("Clear") }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            SectionTitle("Colour"); ColorPicker(color) { color = it }
            SectionTitle("Icon"); IconGrid(icon, Color(color), { icon = it }, Modifier.heightIn(max = 260.dp))
            Button(
                onClick = {
                    val t = Money.parse(target) ?: 0L
                    val n = name.trim()
                    if (t <= 0 || n.isEmpty()) return@Button
                    val g = (orig ?: Goal(bookId = bd.bookId, name = n, target = t, currency = currency, color = color, icon = icon))
                        .copy(name = n, target = t, currency = currency, deadline = deadline, color = color, icon = icon, note = note.trim())
                    scope.launch {
                        if (g.id == 0L) Graph.dao.insertGoal(g) else {
                            val saved = Graph.dao.getGoalEntries(g.id).sumOf { it.amount }
                            Graph.dao.updateGoal(g.copy(reached = saved >= g.target))
                        }
                        back()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Save") }
        }
    }
    if (pickDate) DateDialog(deadline ?: System.currentTimeMillis(), { pickDate = false }) { deadline = it }
    if (pickCurrency) CurrencyPickerSheet(onDismiss = { pickCurrency = false }) { currency = it.code }
}

// ======================= DEBTS =======================

@Composable
fun DebtsScreen(back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val m = LocalMoney.current
    val debts by remember(bd.bookId) { Graph.dao.observeDebts(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val entries by remember { Graph.dao.observeAllDebtEntries() }.collectAsStateWithLifecycle(emptyList())
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Debts & loans", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { go("debtedit?id=0&type=$tab") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { Icon(Icons.Filled.Add, "Add") }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("I owe") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Owed to me") })
            }
            val list = debts.filter { it.type == tab }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val open = list.filter { !it.closed }
                val totalLeft = open.sumOf { d -> bd.rates.toMain((debtTotal(d, entries) - debtPaid(d, entries)).coerceAtLeast(0), d.currency) }
                item {
                    AppCard {
                        Column {
                            Text(if (tab == 0) "Total I still owe" else "Total still owed to me", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (hidden) "••••" else Money.format(totalLeft, bd.main), style = MaterialTheme.typography.headlineSmall, color = if (tab == 0) m.expense else m.income)
                        }
                    }
                }
                if (list.isEmpty()) item { EmptyState(Icons.Filled.Handshake, "Nothing here", if (tab == 0) "Track money you borrowed and pay it back step by step." else "Track money you lent and log repayments.") }
                items(list, key = { it.id }) { d ->
                    val total = debtTotal(d, entries)
                    val paid = debtPaid(d, entries)
                    val frac = if (total > 0) paid.toFloat() / total else 0f
                    AppCard(onClick = { go("debt/${d.id}") }) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SolidIcon(Icons.Filled.Handshake, Color(d.color), 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.person, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (d.closed) "Settled" else d.dueDate?.let { "Due " + Dates.date(it) } ?: ("Since " + Dates.date(d.startDate)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (!d.closed && d.dueDate != null && d.dueDate < System.currentTimeMillis()) m.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(if (hidden) "••••" else Money.format((total - paid).coerceAtLeast(0), d.currency), style = MaterialTheme.typography.titleSmall,
                                    color = if (tab == 0) m.expense else m.income)
                            }
                            Spacer(Modifier.height(10.dp))
                            ProgressBar(frac, Color(d.color))
                            Spacer(Modifier.height(4.dp))
                            Text(if (hidden) "" else "Paid ${Money.format(paid, d.currency)} of ${Money.format(total, d.currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun debtTotal(d: Debt, entries: List<com.spacecay.expenselog.data.DebtEntry>) =
    d.amount + entries.filter { it.debtId == d.id && it.amount < 0 }.sumOf { -it.amount }

private fun debtPaid(d: Debt, entries: List<com.spacecay.expenselog.data.DebtEntry>) =
    entries.filter { it.debtId == d.id && it.amount > 0 }.sumOf { it.amount }

@Composable
fun DebtDetailScreen(id: Long, back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val m = LocalMoney.current
    val scope = rememberCoroutineScope()
    val debts by remember(bd.bookId) { Graph.dao.observeDebts(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val entries by remember(id) { Graph.dao.observeDebtEntries(id) }.collectAsStateWithLifecycle(emptyList())
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    val d = debts.firstOrNull { it.id == id }
    var dialog by remember { mutableIntStateOf(0) } // 1 repay, -1 increase
    var confirm by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar(d?.person ?: "Debt", back) {
                IconButton(onClick = { go("debtedit?id=$id&type=${d?.type ?: 0}") }) { Icon(Icons.Filled.Edit, "Edit") }
                IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        },
    ) { pad ->
        if (d == null) return@Scaffold
        val total = debtTotal(d, entries)
        val paid = debtPaid(d, entries)
        val left = (total - paid).coerceAtLeast(0)
        val payable = d.type == DebtType.PAYABLE
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (payable) "I owe ${d.person}" else "${d.person} owes me", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (hidden) "••••" else Money.format(left, d.currency), style = MaterialTheme.typography.headlineMedium, color = if (payable) m.expense else m.income)
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(if (total > 0) paid.toFloat() / total else 0f, Color(d.color), height = 10.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(if (hidden) "" else "Repaid ${Money.format(paid, d.currency)} of ${Money.format(total, d.currency)}", style = MaterialTheme.typography.bodySmall)
                        d.dueDate?.let { Text("Due ${Dates.date(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { dialog = 1 }, modifier = Modifier.weight(1f), enabled = !d.closed) { Text(if (payable) "Repay" else "Got paid") }
                            OutlinedButton(onClick = { dialog = -1 }, modifier = Modifier.weight(1f)) { Text(if (payable) "Borrow more" else "Lend more") }
                        }
                        if (!d.closed) TextButton(onClick = { scope.launch { Graph.dao.updateDebt(d.copy(closed = true)) } }) { Text("Mark as settled") }
                        else TextButton(onClick = { scope.launch { Graph.dao.updateDebt(d.copy(closed = false)) } }) { Text("Reopen") }
                    }
                }
            }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailLine("Started", Dates.date(d.startDate))
                        DetailLine("Original", Money.format(d.amount, d.currency))
                        d.walletId?.let { DetailLine("Wallet", bd.walletMap[it]?.name ?: "—") }
                        if (d.note.isNotBlank()) DetailLine("Note", d.note)
                    }
                }
            }
            item { SectionTitle("History") }
            items(entries, key = { it.id }) { e ->
                var del by remember { mutableStateOf(false) }
                AppCard(padding = PaddingValues(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (e.amount > 0) (if (payable) "Repaid " else "Received ") else (if (payable) "Borrowed more " else "Lent more ")) +
                                    (if (hidden) "••••" else Money.format(kotlin.math.abs(e.amount), d.currency)),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(Dates.date(e.date) + (e.walletId?.let { " · " + (bd.walletMap[it]?.name ?: "") } ?: "") + (if (e.note.isNotBlank()) " · ${e.note}" else ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { del = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                }
                if (del) ConfirmDialog("Delete entry?", "The linked wallet transaction is removed too.", onDismiss = { del = false }) {
                    scope.launch { Graph.repo.deleteDebtEntry(e) }
                }
            }
        }
        if (dialog != 0) EntryDialog(
            bd,
            title = when { dialog > 0 && payable -> "Repay ${d.person}"; dialog > 0 -> "Payment from ${d.person}"; payable -> "Borrow more"; else -> "Lend more" },
            currency = d.currency,
            walletHint = if ((dialog > 0) == payable) "Pay from wallet" else "Receive into wallet",
            onDismiss = { dialog = 0 },
        ) { amt, date, note, wallet ->
            val sign = dialog
            scope.launch { Graph.repo.addDebtEntry(d, amt * sign, date, note, wallet) }
            dialog = 0
        }
    }
    if (confirm && d != null) {
        var removeTx by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this debt?") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(removeTx, { removeTx = it }); Text("Also delete linked wallet transactions") } },
            confirmButton = { TextButton(onClick = { scope.launch { Graph.repo.deleteDebt(d, removeTx); back() }; confirm = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun DebtEditScreen(id: Long, initialType: Int, back: () -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    var orig by remember { mutableStateOf<Debt?>(null) }
    var type by remember { mutableIntStateOf(initialType) }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var start by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var due by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(Palette.at(14)) }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var pickStart by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }
    var pickWallet by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id != 0L) Graph.dao.getDebt(id)?.let { d ->
            orig = d; type = d.type; person = d.person; amount = Money.toInput(d.amount); currency = d.currency; start = d.startDate
            due = d.dueDate; note = d.note; color = d.color; walletId = d.walletId
        }
    }
    LaunchedEffect(bd.book) { if (currency.isEmpty() && bd.book != null) currency = bd.main }
    val w = walletId?.let { bd.walletMap[it] }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { SimpleTopBar(if (id == 0L) "New debt / loan" else "Edit", back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (id == 0L) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.spacecay.expenselog.ui.components.Pill("I borrowed", type == DebtType.PAYABLE, { type = DebtType.PAYABLE }, color = LocalMoney.current.expense)
                com.spacecay.expenselog.ui.components.Pill("I lent", type == DebtType.RECEIVABLE, { type = DebtType.RECEIVABLE }, color = LocalMoney.current.income)
            }
            OutlinedTextField(person, { person = it }, label = { Text(if (type == DebtType.PAYABLE) "Lender (who I owe)" else "Borrower (who owes me)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            MoneyField(amount, { amount = it }, "Amount", currency)
            PickerField("Currency", currency, null, onClick = { pickCurrency = true })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField("Date", Dates.date(start), Icons.Filled.CalendarToday, onClick = { pickStart = true }, modifier = Modifier.weight(1f))
                PickerField("Due", due?.let { Dates.date(it) } ?: "No due date", Icons.Filled.Event, onClick = { pickDue = true }, modifier = Modifier.weight(1f))
            }
            if (id == 0L) {
                PickerField(if (type == DebtType.PAYABLE) "Money received into" else "Money paid from", w?.name ?: "None — don't touch wallets",
                    w?.let { AppIcons.get(it.icon) } ?: Icons.Filled.AccountBalanceWallet, w?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { pickWallet = true })
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            SectionTitle("Colour"); ColorPicker(color) { color = it }
            Button(
                onClick = {
                    val a = Money.parse(amount) ?: 0L
                    val p = person.trim()
                    if (a <= 0 || p.isEmpty()) return@Button
                    scope.launch {
                        val o = orig
                        if (o == null) Graph.repo.createDebt(Debt(bookId = bd.bookId, type = type, person = p, amount = a, currency = currency, startDate = start,
                            dueDate = due, note = note.trim(), color = color, walletId = walletId))
                        else {
                            Graph.dao.updateDebt(o.copy(person = p, amount = a, currency = currency, startDate = start, dueDate = due, note = note.trim(), color = color))
                            o.txnId?.let { tid -> Graph.dao.getTxn(tid)?.let { t -> Graph.repo.saveTxn(t.copy(amount = a, dateTime = start, payee = p)) } }
                        }
                        back()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Save") }
        }
    }
    if (pickStart) DateDialog(start, { pickStart = false }) { start = it }
    if (pickDue) DateDialog(due ?: System.currentTimeMillis(), { pickDue = false }) { due = it }
    if (pickWallet) WalletPickerSheet(bd, walletId, allowNone = true, onDismiss = { pickWallet = false }) { walletId = it?.id }
    if (pickCurrency) CurrencyPickerSheet(onDismiss = { pickCurrency = false }) { currency = it.code }
}
