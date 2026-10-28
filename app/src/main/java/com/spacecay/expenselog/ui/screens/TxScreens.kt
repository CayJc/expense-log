@file:OptIn(ExperimentalMaterial3Api::class)

package com.spacecay.expenselog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.TxSource
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.ui.Prefill
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.ui.UiBus
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.ConfirmDialog
import com.spacecay.expenselog.ui.components.DonutChart
import com.spacecay.expenselog.ui.components.DonutSlice
import com.spacecay.expenselog.ui.components.EmptyState
import com.spacecay.expenselog.ui.components.Pill
import com.spacecay.expenselog.ui.components.SimpleTopBar
import com.spacecay.expenselog.ui.components.SolidIcon
import com.spacecay.expenselog.ui.components.TxRow
import com.spacecay.expenselog.ui.components.dayGroupedTxns
import com.spacecay.expenselog.ui.rememberBookData
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.util.Photos
import kotlinx.coroutines.launch

@Composable
fun TxDetailScreen(id: Long, back: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val bd = rememberBookData()
    val m = LocalMoney.current
    val scope = rememberCoroutineScope()
    val t by remember(id) { Graph.dao.observeTxn(id) }.collectAsStateWithLifecycle(null)
    var confirm by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var mergePick by remember { mutableStateOf(false) }
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    val tx = t
    val linked = tx?.debtId != null || tx?.goalId != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar("Transaction", back) {
                if (tx != null) {
                    IconButton(onClick = {
                        UiBus.prefill.value = Prefill(tx.type, tx.amount, tx.walletId, tx.toWalletId, tx.categoryId, tx.note, tx.payee, System.currentTimeMillis(), tx.fee)
                        go("txedit?id=0&type=${tx.type}")
                    }) { Icon(Icons.Filled.ContentCopy, "Duplicate") }
                    if (!linked) IconButton(onClick = { mergePick = true }) { Icon(Icons.Filled.MergeType, "Merge with…") }
                    if (!linked) IconButton(onClick = { go("txedit?id=${tx.id}&type=${tx.type}") }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        },
    ) { pad ->
        if (tx == null) return@Scaffold
        val d = bd.display(tx)
        val code = bd.currencyOf(tx.walletId)
        val color = when (tx.type) { TxType.INCOME -> m.income; TxType.EXPENSE -> m.expense; else -> m.transfer }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        SolidIcon(d.icon, d.color, 60.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(d.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (hidden) "••••" else Money.format(if (tx.type == TxType.EXPENSE) -tx.amount else tx.amount, code, tx.type == TxType.INCOME),
                            style = MaterialTheme.typography.headlineMedium, color = color,
                        )
                        if (code != bd.main && !hidden) Text("≈ " + Money.format(bd.mainAmount(tx), bd.main), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!tx.reviewed) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { scope.launch { Graph.repo.saveTxn(tx.copy(reviewed = true)) } }) { Text("Looks right — mark reviewed") }
                        }
                    }
                }
            }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailLine("Type", listOf("Expense", "Income", "Transfer")[tx.type])
                        if (tx.type == TxType.TRANSFER) {
                            DetailLine("From", bd.walletMap[tx.walletId]?.name ?: "—")
                            DetailLine("To", bd.walletMap[tx.toWalletId ?: -1L]?.name ?: "—")
                            tx.toAmount?.let { DetailLine("Received", Money.format(it, bd.currencyOf(tx.toWalletId))) }
                        } else {
                            if (!linked) DetailLine("Category", bd.categoryLabel(tx.categoryId))
                            DetailLine("Wallet", bd.walletMap[tx.walletId]?.name ?: "—")
                        }
                        DetailLine("Date", Dates.fmt("EEEE, d MMMM yyyy · HH:mm", tx.dateTime) + (if (Graph.prefs.ethCal.value == 1) "\n" + com.spacecay.expenselog.util.EthiopianCalendar.format(tx.dateTime) + " (E.C.)" else ""))
                        if (tx.fee > 0) DetailLine("Service fee", Money.format(tx.fee, code))
                        if (tx.payee.isNotBlank()) DetailLine(if (tx.type == TxType.INCOME) "From" else "Paid to", tx.payee)
                        if (tx.note.isNotBlank()) DetailLine("Note", tx.note)
                        DetailLine("Added by", when (tx.source) {
                            TxSource.NOTIFICATION -> "Auto-log (notification)"
                            TxSource.SCREENSHOT -> "Auto-log (screenshot)"
                            TxSource.SHARED_TEXT -> "Shared message"
                            TxSource.RECURRING -> "Recurring transaction"
                            TxSource.DEBT -> "Debt / loan"
                            TxSource.GOAL -> "Saving goal"
                            else -> "You"
                        })
                        tx.ref?.let { DetailLine("Reference", it) }
                    }
                }
            }
            val sid = tx.splitId
            if (sid != null) item {
                val parts by remember(sid) { Graph.dao.observeSplit(sid) }.collectAsStateWithLifecycle(emptyList())
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Split transaction · total ${Money.format(parts.sumOf { it.amount + it.fee }, code)}", style = MaterialTheme.typography.titleSmall)
                        parts.forEach { p ->
                            Row(Modifier.fillMaxWidth().clickable { if (p.id != tx.id) go("tx/${p.id}") }) {
                                Text(bd.categoryLabel(p.categoryId) + if (p.id == tx.id) "  (this)" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(Money.format(p.amount, code), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            if (linked) item {
                AppCard(onClick = { go(if (tx.debtId != null) "debt/${tx.debtId}" else "goal/${tx.goalId}") }) {
                    Text("This entry is managed by a ${if (tx.debtId != null) "debt" else "goal"}. Tap to open it.", color = MaterialTheme.colorScheme.primary)
                }
            }
            val photos = Photos.split(tx.photos)
            if (photos.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEach { p ->
                        AsyncImage(Photos.file(ctx, p), null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(110.dp).clip(RoundedCornerShape(16.dp)).clickable { viewPhoto = p })
                    }
                }
            }
        }
    }
    if (confirm && tx != null) ConfirmDialog("Delete transaction?", "This can't be undone.", onDismiss = { confirm = false }) {
        scope.launch { Graph.repo.deleteTxn(tx.id); back() }
    }
    if (mergePick && tx != null) {
        var candidates by remember { mutableStateOf<List<com.spacecay.expenselog.data.Txn>>(emptyList()) }
        val chosen = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
        var preview by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(tx.id) {
            val day = 86_400_000L
            candidates = Graph.dao.getTxnsBetween(tx.bookId, tx.dateTime - 2 * day, tx.dateTime + 2 * day)
                .filter { it.id != tx.id && it.type == tx.type && it.walletId == tx.walletId && it.debtId == null && it.goalId == null }
                .sortedBy { kotlin.math.abs(it.dateTime - tx.dateTime) }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { mergePick = false },
            title = { Text("Merge with…") },
            text = {
                Column {
                    Text("Pick the entries that belong to this same payment (same wallet, within 2 days).", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (candidates.isEmpty()) Text("Nothing to merge with nearby.")
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(candidates, key = { it.id }) { c ->
                            Row(Modifier.fillMaxWidth().clickable { if (c.id in chosen) chosen.remove(c.id) else chosen.add(c.id) },
                                verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(c.id in chosen, { if (it) chosen.add(c.id) else chosen.remove(c.id) })
                                Column(Modifier.weight(1f)) {
                                    Text(c.payee.ifBlank { bd.display(c).title }, style = MaterialTheme.typography.bodyMedium)
                                    Text(Dates.dateTime(c.dateTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(Money.format(c.amount, bd.currencyOf(c.walletId)), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { preview = true }, enabled = chosen.isNotEmpty()) { Text("Next") } },
            dismissButton = { TextButton(onClick = { mergePick = false }) { Text("Cancel") } },
        )
        if (preview) MergeDialog(listOf(tx) + candidates.filter { it.id in chosen }, bd, onDismiss = { preview = false }) {
            val ids = listOf(tx.id) + chosen
            scope.launch {
                val (id, err) = Graph.repo.mergeTxns(ids)
                mergePick = false
                if (err != null) UiBus.toast.tryEmit(err)
                else if (id != null && id != tx.id) { back(); go("tx/$id") }
                else UiBus.toast.tryEmit("Merged")
            }
        }
    }
    viewPhoto?.let { p ->
        Dialog(onDismissRequest = { viewPhoto = null }) {
            AsyncImage(Photos.file(ctx, p), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { viewPhoto = null })
        }
    }
}

@Composable
fun DetailLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ReviewScreen(back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    val list by remember(bd.bookId) { Graph.dao.observeUnreviewed(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    var confirmAll by remember { mutableStateOf(false) }
    var confirmMerge by remember { mutableStateOf(false) }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    val selecting = selected.isNotEmpty()
    // Drop selections that disappeared (reviewed / deleted elsewhere)
    androidx.compose.runtime.LaunchedEffect(list) { selected.retainAll(list.map { it.id }.toSet()) }

    fun toggle(id: Long) { if (id in selected) selected.remove(id) else selected.add(id) }

    fun delete(t: com.spacecay.expenselog.data.Txn) {
        scope.launch {
            Graph.repo.deleteTxn(t.id)
            val r = snackbar.showSnackbar("Deleted ${Money.format(t.amount, bd.currencyOf(t.walletId))}", actionLabel = "Undo",
                duration = androidx.compose.material3.SnackbarDuration.Short)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) Graph.repo.saveTxn(t.copy(id = 0))
        }
    }

    val picked = list.filter { it.id in selected }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            if (selecting) {
                SimpleTopBar("${selected.size} selected", { selected.clear() }) {
                    IconButton(onClick = { confirmMerge = true }, enabled = selected.size >= 2) {
                        Icon(Icons.Filled.MergeType, "Merge")
                    }
                    IconButton(onClick = {
                        val all = picked
                        selected.clear()
                        scope.launch { all.forEach { Graph.repo.deleteTxn(it.id) }; snackbar.showSnackbar("Deleted ${all.size}") }
                    }) { Icon(Icons.Filled.Delete, "Delete selected") }
                }
            } else {
                SimpleTopBar("Review auto-logged", back) {
                    if (list.isNotEmpty()) {
                        IconButton(onClick = { confirmAll = true }) { Icon(Icons.Filled.Delete, "Delete all") }
                        IconButton(onClick = { scope.launch { Graph.dao.markAllReviewed(bd.bookId) } }) { Icon(Icons.Filled.DoneAll, "Mark all reviewed") }
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    if (selecting) "Tap more payments to add them, then Merge to combine them into one (amounts and fees add up)."
                    else "Tap a payment to set its category, or the bin to delete it. Long-press to select several and merge them — e.g. a payment you had to send in two parts.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            if (list.isEmpty()) item { EmptyState(Icons.Filled.DoneAll, "All caught up", "Nothing waiting for review.") }
            // Suggest merging payments that look like one payment sent in parts.
            if (!selecting) {
                val sorted = list.sortedBy { it.dateTime }
                val suggestions = mutableListOf<List<com.spacecay.expenselog.data.Txn>>()
                var group = mutableListOf<com.spacecay.expenselog.data.Txn>()
                for (t in sorted) {
                    val prev = group.lastOrNull()
                    val samePayee = prev != null && (prev.payee.isBlank() || t.payee.isBlank() || prev.payee.equals(t.payee, true))
                    if (prev != null && t.type == prev.type && t.walletId == prev.walletId && samePayee && t.dateTime - prev.dateTime <= 15 * 60_000L &&
                        t.debtId == null && t.goalId == null && (t.ref == null || t.ref != prev.ref)) group.add(t)
                    else { if (group.size >= 2) suggestions += group.toList(); group = mutableListOf(t) }
                }
                if (group.size >= 2) suggestions += group.toList()
                suggestions.forEach { g ->
                    item(key = "s" + g.joinToString("-") { it.id.toString() }) {
                        val code = bd.currencyOf(g.first().walletId)
                        AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MergeType, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("One payment in ${g.size} parts?", style = MaterialTheme.typography.titleSmall)
                                    Text(g.joinToString(" + ") { Money.plain(it.amount) } + " = " + Money.format(g.sumOf { it.amount }, code) +
                                        " within ${maxOf(1, ((g.last().dateTime - g.first().dateTime) / 60_000L).toInt())} min",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { selected.clear(); selected.addAll(g.map { it.id }); confirmMerge = true }) { Text("Merge") }
                            }
                        }
                    }
                }
            }
            val groups = list.groupBy { Dates.startOfDay(it.dateTime) }.toSortedMap(compareByDescending { it })
            groups.forEach { (day, txs) ->
                item(key = "d$day") {
                    Text(Dates.dayHeader(day) + " · " + Dates.fmt("d MMMM yyyy", day), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                }
                items(txs, key = { it.id }) { t ->
                    val isSel = t.id in selected
                    AppCard(
                        Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                            .then(if (isSel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier),
                        padding = PaddingValues(start = 0.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                TxRow(t, bd, hidden, onLongClick = { toggle(t.id) }) {
                                    if (selecting) toggle(t.id) else go("txedit?id=${t.id}&type=${t.type}")
                                }
                            }
                            if (selecting) androidx.compose.material3.Checkbox(isSel, { toggle(t.id) })
                            else IconButton(onClick = { delete(t) }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (confirmAll) ConfirmDialog("Delete all ${list.size} entries?", "Every payment waiting for review is deleted.", confirm = "Delete all",
        onDismiss = { confirmAll = false }) {
        val all = list.toList()
        scope.launch { all.forEach { Graph.repo.deleteTxn(it.id) } }
    }
    if (confirmMerge) MergeDialog(picked, bd, onDismiss = { confirmMerge = false }) {
        val ids = picked.map { it.id }
        scope.launch {
            val (id, err) = Graph.repo.mergeTxns(ids)
            if (err != null) snackbar.showSnackbar(err) else { selected.clear(); if (id != null) go("txedit?id=$id&type=${picked.first().type}") }
        }
    }
}

/** Shows what the merged entry will look like before merging. */
@Composable
fun MergeDialog(txns: List<com.spacecay.expenselog.data.Txn>, bd: com.spacecay.expenselog.ui.BookData, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val code = bd.currencyOf(txns.firstOrNull()?.walletId)
    val sameWallet = txns.map { it.walletId }.distinct().size == 1
    val sameType = txns.map { it.type }.distinct().size == 1
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge ${txns.size} entries?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                txns.sortedBy { it.dateTime }.forEach { t ->
                    Row {
                        Text(Dates.time(t.dateTime) + "  " + (t.payee.ifBlank { bd.display(t).title }), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(Money.format(t.amount, code), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                androidx.compose.material3.HorizontalDivider()
                Row {
                    Text("Merged", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(Money.format(txns.sumOf { it.amount }, code), fontWeight = FontWeight.SemiBold)
                }
                val fee = txns.sumOf { it.fee }
                if (fee > 0) Text("Fees together: ${Money.format(fee, code)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!sameWallet || !sameType) Text("These are from different wallets or types and can't be merged.", color = MaterialTheme.colorScheme.error)
                else Text("You can set the category and note right after.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDismiss(); onConfirm() }, enabled = sameWallet && sameType) { Text("Merge") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CategoryTxnsScreen(catId: Long, from: Long, to: Long, back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val txns by remember(bd.bookId, from, to) { Graph.dao.observeTxnsBetween(bd.bookId, from, to) }.collectAsStateWithLifecycle(emptyList())
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    val cat = bd.catMap[catId]
    val mine = txns.filter { Stats.counts(it) && bd.topCategory(it.categoryId)?.id == catId }
    val subs = Stats.bySubcategory(txns, bd, catId)
    val total = mine.sumOf { bd.mainAmount(it) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { SimpleTopBar(cat?.name ?: "Category", back) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                AppCard(Modifier.padding(16.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${Dates.date(from)} – ${Dates.date(to - 1)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        if (subs.size > 1) {
                            DonutChart(subs.mapIndexed { i, s -> DonutSlice(s.amount.toFloat(), s.color.copy(alpha = 1f - (i * 0.13f).coerceAtMost(0.7f))) },
                                "Total", if (hidden) "••••" else Money.compact(total), size = 160.dp, thickness = 20.dp)
                            Spacer(Modifier.height(10.dp))
                            subs.forEach { s ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(if (hidden) "••••" else Money.format(s.amount, bd.main), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Text(if (hidden) "••••" else Money.format(total, bd.main), style = MaterialTheme.typography.headlineSmall)
                            Text("${mine.size} transactions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            dayGroupedTxns(mine, bd, hidden) { go("tx/${it.id}") }
        }
    }
}

@Composable
fun SearchScreen(back: () -> Unit, go: (String) -> Unit) {
    val bd = rememberBookData()
    val all by remember(bd.bookId) { Graph.dao.observeAllTxns(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(-1) }
    var walletFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val results = remember(all, q, type, walletFilter, bd) {
        val query = q.trim().lowercase()
        all.filter { t ->
            (type == -1 || t.type == type) &&
                (walletFilter == null || t.walletId == walletFilter || t.toWalletId == walletFilter) &&
                (query.isEmpty() || t.note.lowercase().contains(query) || t.payee.lowercase().contains(query) ||
                    bd.categoryLabel(t.categoryId).lowercase().contains(query) || Money.plain(t.amount).replace(",", "").contains(query.replace(",", "")) ||
                    (t.ref?.lowercase()?.contains(query) == true))
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { SimpleTopBar("Search", back) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Note, payee, category, amount, reference") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp))
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("All", type == -1, { type = -1 })
                    Pill("Expense", type == 0, { type = 0 })
                    Pill("Income", type == 1, { type = 1 })
                    Pill("Transfer", type == 2, { type = 2 })
                    Spacer(Modifier.width(8.dp))
                    bd.wallets.filter { !it.archived }.forEach { w ->
                        Pill(w.name, walletFilter == w.id, { walletFilter = if (walletFilter == w.id) null else w.id }, color = Color(w.color))
                    }
                }
            }
            if (q.isNotBlank() || type != -1 || walletFilter != null) item {
                val inc = Stats.income(results, bd); val exp = Stats.expense(results, bd)
                Text("${results.size} results · in ${if (hidden) "••" else Money.format(inc, bd.main)} · out ${if (hidden) "••" else Money.format(exp, bd.main)}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            if (results.isEmpty()) item { EmptyState(Icons.Filled.SearchOff, "No matches", "Try another word or filter.") }
            dayGroupedTxns(results.take(500), bd, hidden) { go("tx/${it.id}") }
        }
    }
}
