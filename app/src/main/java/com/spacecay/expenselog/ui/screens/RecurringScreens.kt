@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.spacecay.expenselog.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.CatType
import com.spacecay.expenselog.data.Freq
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.Recurring
import com.spacecay.expenselog.data.Template
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.CategoryIcon
import com.spacecay.expenselog.ui.components.CategoryPickerSheet
import com.spacecay.expenselog.ui.components.ConfirmDialog
import com.spacecay.expenselog.ui.components.DateDialog
import com.spacecay.expenselog.ui.components.EmptyState
import com.spacecay.expenselog.ui.components.MoneyField
import com.spacecay.expenselog.ui.components.PickerField
import com.spacecay.expenselog.ui.components.Pill
import com.spacecay.expenselog.ui.components.SectionTitle
import com.spacecay.expenselog.ui.components.SimpleTopBar
import com.spacecay.expenselog.ui.components.WalletPickerSheet
import com.spacecay.expenselog.ui.rememberBookData
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.AppIcons
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import kotlinx.coroutines.launch

private fun freqText(r: Recurring) =
    if (r.interval == 1) Freq.labels[r.frequency] else "Every ${r.interval} ${Freq.units[r.frequency]}s"

@Composable
fun RecurringScreen(back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val m = LocalMoney.current
    val list by remember(bd.bookId) { Graph.dao.observeRecurring(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Recurring", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { go("recurringedit?id=0") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { Icon(Icons.Filled.Add, "Add") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) item { EmptyState(Icons.Filled.Repeat, "No recurring transactions", "Rent, salary, subscriptions — add them once and they're logged automatically.") }
            items(list, key = { it.id }) { r ->
                val c = r.categoryId?.let { bd.catMap[it] }
                AppCard(onClick = { go("recurringedit?id=${r.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(c?.let { AppIcons.get(it.icon) } ?: Icons.Filled.Repeat, c?.let { Color(it.color) } ?: m.transfer, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.note.ifBlank { c?.name ?: if (r.type == TxType.TRANSFER) "Transfer" else "Recurring" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("${freqText(r)}${if (r.confirm) " · reminder" else ""} · ${if (r.active) "next " + Dates.date(r.nextDate) else "paused"}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (hidden) "••••" else Money.format(if (r.type == TxType.EXPENSE) -r.amount else r.amount, bd.currencyOf(r.walletId)),
                            color = when (r.type) { TxType.INCOME -> m.income; TxType.EXPENSE -> m.expense; else -> m.transfer }, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Shared form state used by recurring and template editors. */
@Composable
private fun TxFormFields(
    bd: BookData,
    type: Int, onType: (Int) -> Unit,
    amount: String, onAmount: (String) -> Unit,
    walletId: Long?, onWallet: (Long?) -> Unit,
    toWalletId: Long?, onToWallet: (Long?) -> Unit,
    categoryId: Long?, onCategory: (Long?) -> Unit,
    note: String, onNote: (String) -> Unit,
) {
    val m = LocalMoney.current
    var pickWallet by remember { mutableIntStateOf(0) }
    var pickCat by remember { mutableStateOf(false) }
    val w = walletId?.let { bd.walletMap[it] }
    val tw = toWalletId?.let { bd.walletMap[it] }
    val c = categoryId?.let { bd.catMap[it] }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Expense", type == 0, { onType(0); onCategory(null) }, color = m.expense)
        Pill("Income", type == 1, { onType(1); onCategory(null) }, color = m.income)
        Pill("Transfer", type == 2, { onType(2); onCategory(null) }, color = m.transfer)
    }
    MoneyField(amount, onAmount, "Amount", w?.currency ?: bd.main)
    PickerField(if (type == 2) "From" else "Wallet", w?.name ?: "Choose wallet", w?.let { AppIcons.get(it.icon) } ?: Icons.Filled.AccountBalanceWallet,
        w?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, onClick = { pickWallet = 1 })
    if (type == 2) {
        PickerField("To", tw?.name ?: "Choose wallet", tw?.let { AppIcons.get(it.icon) } ?: Icons.Filled.AccountBalanceWallet,
            tw?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, onClick = { pickWallet = 2 })
    } else {
        PickerField("Category", c?.let { bd.categoryLabel(it.id) } ?: "Choose category", c?.let { AppIcons.get(it.icon) } ?: Icons.Filled.HelpOutline,
            c?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant, onClick = { pickCat = true })
    }
    OutlinedTextField(note, onNote, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (pickWallet != 0) WalletPickerSheet(bd, if (pickWallet == 1) walletId else toWalletId, onDismiss = { pickWallet = 0 }) {
        if (pickWallet == 1) onWallet(it?.id) else onToWallet(it?.id)
    }
    if (pickCat) CategoryPickerSheet(bd, if (type == 1) CatType.INCOME else CatType.EXPENSE, categoryId, onDismiss = { pickCat = false }) { onCategory(it.id) }
}

@Composable
fun RecurringEditScreen(id: Long, back: () -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    var orig by remember { mutableStateOf<Recurring?>(null) }
    var type by remember { mutableIntStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var toWalletId by remember { mutableStateOf<Long?>(null) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var freq by remember { mutableIntStateOf(Freq.MONTHLY) }
    var interval by remember { mutableStateOf("1") }
    var start by remember { mutableLongStateOf(Dates.combine(System.currentTimeMillis(), 9, 0)) }
    var end by remember { mutableStateOf<Long?>(null) }
    var active by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(id, bd.wallets.isNotEmpty()) {
        if (id != 0L && orig == null) Graph.dao.getRecurring(id)?.let { r ->
            orig = r; type = r.type; amount = Money.toInput(r.amount); walletId = r.walletId; toWalletId = r.toWalletId; categoryId = r.categoryId
            note = r.note; freq = r.frequency; interval = r.interval.toString(); start = r.startDate; end = r.endDate; active = r.active; confirm = r.confirm
        }
        if (walletId == null) walletId = bd.wallets.firstOrNull { !it.archived }?.id
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar(if (id == 0L) "New recurring" else "Edit recurring", back) {
                if (id != 0L) IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TxFormFields(bd, type, { type = it }, amount, { amount = it }, walletId, { walletId = it }, toWalletId, { toWalletId = it }, categoryId, { categoryId = it }, note, { note = it })
            SectionTitle("Repeat")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Freq.labels.forEachIndexed { i, l -> Pill(l, freq == i, { freq = i }) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Every", modifier = Modifier.padding(end = 10.dp))
                OutlinedTextField(interval, { v -> interval = v.filter { it.isDigit() }.take(3) }, singleLine = true, modifier = Modifier.width(90.dp))
                Text(" ${Freq.units[freq]}(s)", modifier = Modifier.padding(start = 10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField("Starts", Dates.date(start), Icons.Filled.CalendarToday, onClick = { pickStart = true }, modifier = Modifier.weight(1f))
                PickerField("Ends", end?.let { Dates.date(it) } ?: "Never", Icons.Filled.Event, onClick = { pickEnd = true }, modifier = Modifier.weight(1f))
            }
            if (end != null) TextButton(onClick = { end = null }) { Text("Remove end date") }
            if (id != 0L) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active", Modifier.weight(1f)); Switch(active, { active = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remind me first (bill)")
                    Text("Get a notification with Add / Skip instead of logging it automatically", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(confirm, { confirm = it })
            }
            Text(if (confirm) "On the due date you'll be asked to add or skip it." else "Transactions are added automatically on each due date (including any missed while the phone was off).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    val a = Money.parse(amount) ?: 0L
                    val w = walletId ?: return@Button
                    if (a <= 0) return@Button
                    if (type == 2 && (toWalletId == null || toWalletId == w)) return@Button
                    val iv = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val o = orig
                    val next = when {
                        o == null -> start
                        o.startDate != start || o.frequency != freq || o.interval != iv -> {
                            // recompute next from start, skipping past dates already logged
                            var n = start
                            val tmp = (o.copy(startDate = start, frequency = freq, interval = iv))
                            while (n < o.nextDate) n = Graph.repo.nextOccurrence(tmp, n)
                            if (start > System.currentTimeMillis()) start else n
                        }
                        else -> o.nextDate
                    }
                    val r = (o ?: Recurring(bookId = bd.bookId, type = type, amount = a, walletId = w, startDate = start, nextDate = start)).copy(
                        type = type, amount = a, walletId = w, toWalletId = if (type == 2) toWalletId else null,
                        categoryId = if (type == 2) null else categoryId, note = note.trim(), frequency = freq, interval = iv,
                        startDate = start, nextDate = next, endDate = end, active = active, confirm = confirm,
                    )
                    scope.launch {
                        if (r.id == 0L) Graph.dao.insertRecurring(r) else Graph.dao.updateRecurring(r)
                        Graph.repo.runDueRecurring()
                        Graph.repo.notifyDueBills(Graph.app)
                        back()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Save") }
        }
    }
    if (pickStart) DateDialog(start, { pickStart = false }) { start = Dates.combine(it, 9, 0) }
    if (pickEnd) DateDialog(end ?: System.currentTimeMillis(), { pickEnd = false }) { end = Dates.day(it).end - 1 }
    if (confirm) ConfirmDialog("Delete recurring?", "Transactions already created stay.", onDismiss = { confirm = false }) {
        scope.launch { Graph.dao.deleteRecurring(id); back() }
    }
}

@Composable
fun TemplatesScreen(back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val m = LocalMoney.current
    val list by remember(bd.bookId) { Graph.dao.observeTemplates(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Templates", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { go("templateedit?id=0") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { Icon(Icons.Filled.Add, "Add") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) item { EmptyState(Icons.Filled.Bookmarks, "No templates", "Save transactions you repeat often — like your daily taxi — and add them with one tap.") }
            items(list, key = { it.id }) { t ->
                val c = t.categoryId?.let { bd.catMap[it] }
                AppCard(onClick = { go("txedit?id=0&type=${t.type}&template=${t.id}") }, padding = PaddingValues(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(c?.let { AppIcons.get(it.icon) } ?: Icons.Filled.Bookmarks, c?.let { Color(it.color) } ?: m.transfer, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(c?.name, t.walletId?.let { bd.walletMap[it]?.name }).joinToString(" · ") + " · tap to use",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.format(t.amount, bd.currencyOf(t.walletId)), fontWeight = FontWeight.SemiBold,
                            color = when (t.type) { TxType.INCOME -> m.income; TxType.EXPENSE -> m.expense; else -> m.transfer })
                        IconButton(onClick = { go("templateedit?id=${t.id}") }) { Icon(Icons.Filled.Edit, "Edit") }
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateEditScreen(id: Long, back: () -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    var orig by remember { mutableStateOf<Template?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableIntStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var toWalletId by remember { mutableStateOf<Long?>(null) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id != 0L) Graph.dao.getTemplate(id)?.let { t ->
            orig = t; name = t.name; type = t.type; amount = Money.toInput(t.amount); walletId = t.walletId; toWalletId = t.toWalletId
            categoryId = t.categoryId; note = t.note
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar(if (id == 0L) "New template" else "Edit template", back) {
                if (id != 0L) IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Template name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TxFormFields(bd, type, { type = it }, amount, { amount = it }, walletId, { walletId = it }, toWalletId, { toWalletId = it }, categoryId, { categoryId = it }, note, { note = it })
            Text("Amount can be left empty and typed each time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    val n = name.trim().ifBlank { note.trim().ifBlank { "Template" } }
                    val t = (orig ?: Template(bookId = bd.bookId, name = n, type = type, amount = 0)).copy(
                        name = n, type = type, amount = Money.parse(amount) ?: 0L, walletId = walletId,
                        toWalletId = if (type == 2) toWalletId else null, categoryId = if (type == 2) null else categoryId, note = note.trim(),
                    )
                    scope.launch { if (t.id == 0L) Graph.dao.insertTemplate(t) else Graph.dao.updateTemplate(t); back() }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Save") }
        }
    }
    if (confirm) ConfirmDialog("Delete template?", "", onDismiss = { confirm = false }) { scope.launch { Graph.dao.deleteTemplate(id); back() } }
}
