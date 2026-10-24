@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.spacecay.expenselog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.TxSource
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.data.Txn
import com.spacecay.expenselog.data.Wallet
import com.spacecay.expenselog.data.WalletType
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.ColorPicker
import com.spacecay.expenselog.ui.components.ConfirmDialog
import com.spacecay.expenselog.ui.components.CurrencyPickerSheet
import com.spacecay.expenselog.ui.components.EmptyState
import com.spacecay.expenselog.ui.components.IconGrid
import com.spacecay.expenselog.ui.components.LineChart
import com.spacecay.expenselog.ui.components.MoneyField
import com.spacecay.expenselog.ui.components.Pill
import com.spacecay.expenselog.ui.components.PickerField
import com.spacecay.expenselog.ui.components.SectionTitle
import com.spacecay.expenselog.ui.components.SettingRow
import com.spacecay.expenselog.ui.components.SimpleTopBar
import com.spacecay.expenselog.ui.components.SolidIcon
import com.spacecay.expenselog.ui.components.dayGroupedTxns
import com.spacecay.expenselog.ui.rememberBookData
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.AppIcons
import com.spacecay.expenselog.util.Currencies
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.util.Palette
import kotlinx.coroutines.launch

@Composable
fun WalletDetailScreen(id: Long, back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val m = LocalMoney.current
    val scope = rememberCoroutineScope()
    val w = bd.walletMap[id]
    val txns by remember(id) { Graph.dao.observeWalletTxns(id) }.collectAsStateWithLifecycle(emptyList())
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    var adjust by remember { mutableStateOf(false) }
    LaunchedEffect(bd.wallets) { if (bd.wallets.isNotEmpty() && bd.walletMap[id] == null) back() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar(w?.name ?: "Wallet", back) {
                IconButton(onClick = { adjust = true }) { Icon(Icons.Filled.Tune, "Adjust balance") }
                IconButton(onClick = { go("walletedit?id=$id") }) { Icon(Icons.Filled.Edit, "Edit") }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { go("txedit?id=0&type=0&wallet=$id") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                Icon(Icons.Filled.Add, "Add")
            }
        },
    ) { pad ->
        if (w == null) return@Scaffold
        val bal = bd.balance(w)
        // running balance trend (oldest -> newest)
        val trend = remember(txns, w) {
            var b = w.initialBalance
            val pts = mutableListOf(b)
            txns.sortedBy { it.dateTime }.forEach { t ->
                b += when {
                    t.type == TxType.INCOME -> t.amount
                    t.type == TxType.EXPENSE -> -(t.amount + t.fee)
                    t.walletId == w.id -> -(t.amount + t.fee)
                    else -> t.toAmount ?: t.amount
                }
                pts += b
            }
            pts.takeLast(60)
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(w.color), Color(w.color).copy(alpha = 0.75f)))).padding(20.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.get(w.icon), null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(WalletType.labels.getOrElse(w.type) { "" } + " · " + w.currency, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(if (hidden) "••••" else Money.format(bal, w.currency), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        if (w.currency != bd.main && !hidden) Text("≈ " + Money.format(bd.rates.toMain(bal, w.currency), bd.main), color = Color.White.copy(alpha = 0.8f))
                        if (trend.size > 2) { Spacer(Modifier.height(10.dp)); LineChart(trend, Color.White, height = 70.dp) }
                    }
                }
            }
            item {
                val income = txns.filter { it.type == TxType.INCOME && Stats.counts(it) }.sumOf { it.amount }
                val expense = txns.filter { it.type == TxType.EXPENSE && Stats.counts(it) }.sumOf { it.amount }
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    Row {
                        SmallStat("Money in", if (hidden) "••••" else Money.format(income, w.currency), m.income, Modifier.weight(1f))
                        SmallStat("Money out", if (hidden) "••••" else Money.format(expense, w.currency), m.expense, Modifier.weight(1f))
                    }
                }
            }
            val check = Graph.prefs.balanceCheck(w.id)
            if (check != null && !hidden) item {
                val (reported, at) = check
                // Balance at the time of the message vs the app's balance now (later entries shift it).
                val after = txns.filter { it.dateTime > at }.sumOf { t ->
                    when {
                        t.type == TxType.INCOME -> t.amount
                        t.type == TxType.EXPENSE -> -(t.amount + t.fee)
                        t.walletId == w.id -> -(t.amount + t.fee)
                        else -> t.toAmount ?: t.amount
                    }
                }
                val expected = reported + after
                val diff = expected - bal
                AppCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    Column {
                        Text(if (kotlin.math.abs(diff) < 100) "✓ Matches your ${w.name} balance" else "Balance doesn't match",
                            style = MaterialTheme.typography.titleSmall, color = if (kotlin.math.abs(diff) < 100) m.income else m.expense)
                        Text("Last message (${Dates.dateTime(at)}) said ${Money.format(reported, w.currency)}." +
                            if (kotlin.math.abs(diff) >= 100) " Expected now: ${Money.format(expected, w.currency)}, app shows ${Money.format(bal, w.currency)}." else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (kotlin.math.abs(diff) >= 100) TextButton(onClick = {
                            scope.launch {
                                Graph.repo.saveTxn(Txn(bookId = w.bookId, type = if (diff > 0) TxType.INCOME else TxType.EXPENSE, amount = kotlin.math.abs(diff),
                                    walletId = w.id, note = "Balance adjustment (from ${w.name} message)", dateTime = System.currentTimeMillis(), excludeFromStats = true))
                            }
                        }) { Text("Fix it — add a ${Money.format(kotlin.math.abs(diff), w.currency)} adjustment") }
                    }
                }
            }
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { go("txedit?id=0&type=2&wallet=$id") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.SwapHoriz, null); Spacer(Modifier.width(6.dp)); Text("Transfer")
                    }
                    OutlinedButton(onClick = { adjust = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Tune, null); Spacer(Modifier.width(6.dp)); Text("Adjust")
                    }
                }
            }
            if (txns.isEmpty()) item { EmptyState(Icons.Filled.SwapHoriz, "No activity yet", "Transactions in this wallet will show here.") }
            dayGroupedTxns(txns, bd, hidden) { go("tx/${it.id}") }
        }
        if (adjust) {
            var text by remember { mutableStateOf(Money.toInput(bal)) }
            AlertDialog(
                onDismissRequest = { adjust = false },
                title = { Text("Adjust balance") },
                text = {
                    Column {
                        Text("Enter the real balance. The difference is recorded as an adjustment.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        MoneyField(text, { text = it }, "Actual balance", w.currency)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val target = Money.parse(text)
                        if (target != null && target != bal) {
                            val diff = target - bal
                            scope.launch {
                                Graph.repo.saveTxn(
                                    Txn(bookId = w.bookId, type = if (diff > 0) TxType.INCOME else TxType.EXPENSE, amount = kotlin.math.abs(diff), walletId = w.id,
                                        note = "Balance adjustment", dateTime = System.currentTimeMillis(), source = TxSource.MANUAL, excludeFromStats = true)
                                )
                            }
                        }
                        adjust = false
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { adjust = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
fun WalletEditScreen(id: Long, back: () -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == 0L) }
    var orig by remember { mutableStateOf<Wallet?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableIntStateOf(WalletType.CASH) }
    var currency by remember { mutableStateOf("") }
    var initial by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(Palette.at(5)) }
    var icon by remember { mutableStateOf("wallet") }
    var exclude by remember { mutableStateOf(false) }
    var archived by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf("") }
    var pickCurrency by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var txCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(id) {
        if (id != 0L) Graph.dao.getWallet(id)?.let { w ->
            orig = w; name = w.name; type = w.type; currency = w.currency; initial = Money.toInput(w.initialBalance)
            color = w.color; icon = w.icon; exclude = w.excludeFromTotal; archived = w.archived; limit = Money.toInput(w.creditLimit)
            txCount = Graph.dao.countWalletTxns(id)
        }
        loaded = true
    }
    LaunchedEffect(bd.book) { if (currency.isEmpty() && bd.book != null) currency = bd.main }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar(if (id == 0L) "New wallet" else "Edit wallet", back) {
                if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        },
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SolidIcon(AppIcons.get(icon), Color(color), 56.dp)
                Spacer(Modifier.width(14.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Wallet name") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            SectionTitle("Type")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WalletType.labels.forEachIndexed { i, l -> Pill(l, type == i, { type = i }) }
            }
            PickerField("Currency", "$currency · ${Currencies.info(currency).name}", null, onClick = { pickCurrency = true })
            if (currency != bd.main && !bd.rates.has(currency)) {
                Text("Tip: set a rate for $currency in Currencies so totals convert correctly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            MoneyField(initial, { initial = it }, "Starting balance", currency)
            if (type == WalletType.CREDIT) MoneyField(limit, { limit = it }, "Credit limit", currency)
            SectionTitle("Colour")
            ColorPicker(color) { color = it }
            SectionTitle("Icon")
            IconGrid(icon, Color(color), { icon = it }, Modifier.heightIn(max = 260.dp))
            AppCard(padding = PaddingValues(4.dp)) {
                Column {
                    SettingRow(null, "Include in total balance", "Turn off for savings you don't want counted", trailing = { Switch(!exclude, { exclude = !it }) })
                    if (id != 0L) SettingRow(Icons.Filled.Archive, "Archived", "Hidden from pickers and totals", trailing = { Switch(archived, { archived = it }) })
                }
            }
            Button(
                onClick = {
                    val n = name.trim()
                    if (n.isEmpty()) return@Button
                    val w = (orig ?: Wallet(bookId = bd.bookId, name = n, currency = currency, color = color, icon = icon, sortOrder = bd.wallets.size)).copy(
                        name = n, type = type, currency = currency, initialBalance = Money.parse(initial) ?: 0L, color = color, icon = icon,
                        excludeFromTotal = exclude, archived = archived, creditLimit = Money.parse(limit) ?: 0L,
                    )
                    scope.launch {
                        if (w.id == 0L) Graph.dao.insertWallet(w) else Graph.dao.updateWallet(w)
                        if (currency != bd.main && !bd.rates.has(currency)) Graph.repo.setRate(bd.bookId, currency, 1.0)
                        Graph.repo.refreshWidget()
                        back()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Save") }
        }
    }
    if (pickCurrency) CurrencyPickerSheet(onDismiss = { pickCurrency = false }) { currency = it.code }
    if (confirmDelete) ConfirmDialog(
        "Delete wallet?",
        if (txCount > 0) "This also deletes its $txCount transactions. Archive it instead if you want to keep history." else "This wallet has no transactions.",
        onDismiss = { confirmDelete = false },
    ) { scope.launch { Graph.repo.deleteWallet(id); back() } }
}

@Composable
fun WalletsManageScreen(back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Wallets", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { go("walletedit?id=0") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { Icon(Icons.Filled.Add, "Add") }
        },
    ) { pad ->
        val list = bd.wallets
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { w ->
                val i = list.indexOf(w)
                AppCard(padding = PaddingValues(4.dp)) { Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { WalletRow(w, bd, hidden) { go("walletedit?id=${w.id}") } }
                        Column {
                            IconButton(onClick = {
                                if (i > 0) scope.launch {
                                    val mut = list.toMutableList(); val a = mut[i - 1]; mut[i - 1] = w; mut[i] = a
                                    Graph.dao.updateWallets(mut.mapIndexed { idx, x -> x.copy(sortOrder = idx) })
                                }
                            }, modifier = Modifier.height(32.dp)) { Icon(Icons.Filled.ArrowUpward, "Up") }
                            IconButton(onClick = {
                                if (i < list.size - 1) scope.launch {
                                    val mut = list.toMutableList(); val a = mut[i + 1]; mut[i + 1] = w; mut[i] = a
                                    Graph.dao.updateWallets(mut.mapIndexed { idx, x -> x.copy(sortOrder = idx) })
                                }
                            }, modifier = Modifier.height(32.dp)) { Icon(Icons.Filled.ArrowDownward, "Down") }
                        }
                    }
                    if (w.archived) Text("Archived", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 66.dp, bottom = 6.dp))
                } }
            }
        }
    }
}
