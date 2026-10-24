@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.spacecay.expenselog.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spacecay.expenselog.data.CatType
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.PayeeRule
import com.spacecay.expenselog.data.Template
import com.spacecay.expenselog.data.TxSource
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.data.Txn
import com.spacecay.expenselog.ui.UiBus
import com.spacecay.expenselog.ui.components.CategoryPickerSheet
import com.spacecay.expenselog.ui.components.DateDialog
import com.spacecay.expenselog.ui.components.MoneyField
import com.spacecay.expenselog.ui.components.PickerField
import com.spacecay.expenselog.ui.components.TimeDialog
import com.spacecay.expenselog.ui.components.WalletPickerSheet
import com.spacecay.expenselog.ui.rememberBookData
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.AppIcons
import com.spacecay.expenselog.util.Calc
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.util.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.roundToLong

@Composable
fun TxEditScreen(
    id: Long,
    initialType: Int,
    templateId: Long,
    presetWalletId: Long,
    onClose: () -> Unit,
    onManageCategories: () -> Unit,
) {
    val ctx = LocalContext.current
    val bd = rememberBookData()
    val m = LocalMoney.current
    val scope = rememberCoroutineScope()
    val templates by remember(bd.bookId) { Graph.dao.observeTemplates(bd.bookId) }.collectAsStateWithLifecycle(emptyList())

    var loaded by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Txn?>(null) }
    var type by remember { mutableStateOf(initialType.coerceIn(0, 2)) }
    var expr by remember { mutableStateOf("") }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var toWalletId by remember { mutableStateOf<Long?>(null) }
    var toAmountText by remember { mutableStateOf("") }
    var toAmountEdited by remember { mutableStateOf(false) }
    var feeText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var dateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val photos = remember { mutableStateListOf<String>() }
    var ref by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf(TxSource.MANUAL) }
    var showPad by remember { mutableStateOf(id == 0L) }

    var pickWallet by remember { mutableStateOf(0) } // 0 none, 1 from, 2 to
    var pickCategory by remember { mutableStateOf(false) }
    // Split: extra parts (category + amount); whatever is left stays on the main category.
    val splitCats = remember { mutableStateListOf<Long?>() }
    val splitAmts = remember { mutableStateListOf<String>() }
    var pickSplitIdx by remember { mutableStateOf(-1) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyTemplate(t: Template) {
        type = t.type
        expr = Money.toInput(t.amount)
        t.walletId?.let { walletId = it }
        toWalletId = t.toWalletId
        categoryId = t.categoryId
        note = t.note
    }

    // Load existing / prefill / template once book data is ready.
    LaunchedEffect(bd.wallets.isNotEmpty()) {
        if (loaded || bd.wallets.isEmpty()) return@LaunchedEffect
        if (id != 0L) {
            Graph.dao.getTxn(id)?.let { t ->
                original = t
                type = t.type; expr = Money.toInput(t.amount); walletId = t.walletId; toWalletId = t.toWalletId
                t.toAmount?.let { toAmountText = Money.toInput(it); toAmountEdited = true }
                feeText = Money.toInput(t.fee); categoryId = t.categoryId; note = t.note; payee = t.payee
                dateTime = t.dateTime; photos.addAll(Photos.split(t.photos)); ref = t.ref; source = t.source
            }
        } else {
            val p = UiBus.prefill.value
            if (p != null) {
                UiBus.prefill.value = null
                type = p.type; expr = Money.toInput(p.amount); walletId = p.walletId; toWalletId = p.toWalletId
                categoryId = p.categoryId; note = p.note; payee = p.payee; p.dateTime?.let { dateTime = it }
                feeText = Money.toInput(p.fee); ref = p.ref; source = p.source
                p.photoUri?.let { u -> withContext(Dispatchers.IO) { Photos.import(ctx, Uri.parse(u)) }?.let { photos.add(it) } }
                showPad = p.amount == 0L
            }
            if (templateId != 0L) Graph.dao.getTemplate(templateId)?.let { applyTemplate(it) }
            if (presetWalletId != 0L) walletId = presetWalletId
            if (walletId == null) walletId = bd.wallets.firstOrNull { !it.archived }?.id
        }
        loaded = true
    }

    val wallet = walletId?.let { bd.walletMap[it] }
    val toWallet = toWalletId?.let { bd.walletMap[it] }
    val code = wallet?.currency ?: bd.main
    val evaluated = Calc.eval(expr)
    val amountCents = evaluated?.let { (it * 100).roundToLong() } ?: 0L
    val crossCurrency = type == TxType.TRANSFER && wallet != null && toWallet != null && wallet.currency != toWallet.currency
    LaunchedEffect(amountCents, walletId, toWalletId, bd.rateList) {
        if (crossCurrency && !toAmountEdited && wallet != null && toWallet != null) {
            toAmountText = Money.toInput(bd.rates.convert(amountCents, wallet.currency, toWallet.currency))
        }
    }
    val typeColor = when (type) { TxType.INCOME -> m.income; TxType.EXPENSE -> m.expense; else -> m.transfer }
    val category = categoryId?.let { bd.catMap[it] }
    // Drop a category that doesn't match the type
    LaunchedEffect(type) {
        if (loaded) { splitCats.clear(); splitAmts.clear() }
        val c = categoryId?.let { bd.catMap[it] }
        if (type == TxType.TRANSFER || (c != null && c.type != (if (type == TxType.INCOME) CatType.INCOME else CatType.EXPENSE))) categoryId = null
    }

    val noteSrc = remember { MutableInteractionSource() }
    val payeeSrc = remember { MutableInteractionSource() }
    LaunchedEffect(noteSrc) { noteSrc.interactions.collect { if (it is FocusInteraction.Focus) showPad = false } }
    LaunchedEffect(payeeSrc) { payeeSrc.interactions.collect { if (it is FocusInteraction.Focus) showPad = false } }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        scope.launch {
            uris.forEach { u -> withContext(Dispatchers.IO) { Photos.import(ctx, u) }?.let { photos.add(it) } }
        }
    }

    fun save(andNew: Boolean) {
        val amt = amountCents
        if (amt <= 0) { error = "Enter an amount"; showPad = true; return }
        val w = wallet ?: run { error = "Choose a wallet"; return }
        if (type == TxType.TRANSFER) {
            if (toWallet == null) { error = "Choose the wallet to transfer to"; return }
            if (toWallet.id == w.id) { error = "Pick two different wallets"; return }
        }
        val fee = Money.parse(feeText) ?: 0L
        val toAmt = if (crossCurrency) Money.parse(toAmountText) else null
        val base = original
        val partSum = splitAmts.sumOf { Money.parse(it) ?: 0L }
        val splitting = id == 0L && type != TxType.TRANSFER && splitAmts.isNotEmpty()
        if (splitting) {
            if (splitAmts.any { (Money.parse(it) ?: 0L) <= 0L } || splitCats.any { it == null }) { error = "Give every split part a category and an amount"; return }
            if (partSum >= amt) { error = "Split parts add up to more than the total"; return }
        }
        val t = Txn(
            id = base?.id ?: 0L,
            bookId = base?.bookId ?: bd.bookId,
            type = type,
            amount = if (splitting) amt - partSum else amt,
            walletId = w.id,
            toWalletId = if (type == TxType.TRANSFER) toWallet?.id else null,
            toAmount = toAmt,
            fee = if (type == TxType.INCOME) 0 else fee,
            categoryId = if (type == TxType.TRANSFER) null else categoryId,
            note = note.trim(),
            payee = payee.trim(),
            dateTime = dateTime,
            photos = Photos.join(photos),
            source = base?.source ?: source,
            debtId = base?.debtId,
            goalId = base?.goalId,
            recurringId = base?.recurringId,
            ref = ref,
            reviewed = true,
            excludeFromStats = base?.excludeFromStats ?: false,
            createdAt = base?.createdAt ?: System.currentTimeMillis(),
        )
        scope.launch {
            val mainId = Graph.repo.saveTxn(t)
            if (splitting) {
                Graph.dao.updateTxn(t.copy(id = mainId, splitId = mainId))
                splitCats.forEachIndexed { i, c ->
                    Graph.dao.insertTxn(t.copy(id = 0, amount = Money.parse(splitAmts[i]) ?: 0L, categoryId = c, fee = 0, photos = "", ref = null, splitId = mainId))
                }
                Graph.repo.refreshWidget()
                splitCats.clear(); splitAmts.clear()
            }
            // Learn: auto-logged payee -> chosen category
            val cat = t.categoryId
            if (cat != null && t.payee.isNotBlank() && t.source != TxSource.MANUAL) {
                val key = t.payee.lowercase().take(40)
                val rules = Graph.dao.getRules(t.bookId)
                if (rules.none { it.keyword == key }) Graph.dao.insertRule(PayeeRule(bookId = t.bookId, keyword = key, categoryId = cat))
            }
            if (andNew) {
                expr = ""; note = ""; payee = ""; photos.clear(); feeText = ""; ref = null; original = null
                showPad = true
                UiBus.toast.tryEmit("Saved")
            } else onClose()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        // ---- Header ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Text(if (id == 0L) "New transaction" else "Edit transaction", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Save as template") }, onClick = { menu = false; templateName = note.ifBlank { category?.name ?: "" } })
                    if (id == 0L) DropdownMenuItem(text = { Text("Save & add another") }, onClick = { menu = false; save(true) })
                }
            }
            IconButton(onClick = { save(false) }) { Icon(Icons.Filled.Check, "Save", tint = MaterialTheme.colorScheme.primary) }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("Expense", "Income", "Transfer").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = type == i,
                    onClick = { type = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = when (i) { 0 -> m.expense; 1 -> m.income; else -> m.transfer }.copy(alpha = 0.18f),
                    ),
                ) { Text(label) }
            }
        }

        // ---- Amount ----
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(20.dp))
                .background(typeColor.copy(alpha = 0.10f)).clickable { showPad = true }.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                when (type) { TxType.EXPENSE -> "Amount spent"; TxType.INCOME -> "Amount received"; else -> "Amount sent" } + " · $code",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Money.prefix(code).trim(), style = MaterialTheme.typography.titleLarge, color = typeColor, modifier = Modifier.padding(bottom = 6.dp, end = 6.dp))
                Text(Calc.pretty(expr), fontSize = if (expr.length > 12) 30.sp else 40.sp, fontWeight = FontWeight.Bold, color = typeColor, maxLines = 1)
            }
            if (Calc.hasOp(expr) && evaluated != null) {
                Text("= " + Money.format(amountCents, code), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (code != bd.main && amountCents > 0) {
                Text("≈ " + Money.format(bd.rates.toMain(amountCents, code), bd.main), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---- Fields ----
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (id == 0L && templates.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { t ->
                        val c = t.categoryId?.let { bd.catMap[it] }
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { applyTemplate(t) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (c != null) { Icon(AppIcons.get(c.icon), null, tint = Color(c.color), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                            Text(t.name, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (type == TxType.TRANSFER) {
                PickerField("From", wallet?.name ?: "Choose wallet", wallet?.let { AppIcons.get(it.icon) } ?: Icons.Filled.AccountBalanceWallet,
                    wallet?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, onClick = { showPad = false; pickWallet = 1 })
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { val a = walletId; walletId = toWalletId; toWalletId = a; toAmountEdited = false }) { Icon(Icons.Filled.SwapVert, "Swap") }
                }
                PickerField("To", toWallet?.name ?: "Choose wallet", toWallet?.let { AppIcons.get(it.icon) } ?: Icons.Filled.AccountBalanceWallet,
                    toWallet?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, onClick = { showPad = false; pickWallet = 2 })
                if (crossCurrency && toWallet != null && wallet != null) {
                    MoneyField(toAmountText, { toAmountText = it; toAmountEdited = true }, "Amount received", toWallet.currency)
                    val got = Money.parse(toAmountText) ?: 0
                    if (amountCents > 0 && got > 0) {
                        Text("Rate used: 1 ${wallet.currency} = ${Money.formatRate(got.toDouble() / amountCents)} ${toWallet.currency}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                PickerField("Category", category?.let { bd.categoryLabel(it.id) } ?: "Choose category",
                    category?.let { AppIcons.get(it.icon) } ?: Icons.Filled.HelpOutline,
                    category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { showPad = false; pickCategory = true })
                if (id == 0L) {
                    splitCats.indices.forEach { i ->
                        val sc = splitCats[i]?.let { bd.catMap[it] }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PickerField("Split part ${i + 2}", sc?.let { bd.categoryLabel(it.id) } ?: "Category", sc?.let { AppIcons.get(it.icon) } ?: Icons.Filled.HelpOutline,
                                sc?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant, onClick = { showPad = false; pickSplitIdx = i }, modifier = Modifier.weight(1.2f))
                            MoneyField(splitAmts[i], { splitAmts[i] = it }, "Amount", code, Modifier.weight(1f))
                            IconButton(onClick = { splitCats.removeAt(i); splitAmts.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove part") }
                        }
                    }
                    if (splitAmts.isNotEmpty()) {
                        val rest = amountCents - splitAmts.sumOf { Money.parse(it) ?: 0L }
                        Text("${category?.name ?: "Main category"} gets the rest: ${Money.format(rest, code)}",
                            style = MaterialTheme.typography.bodySmall, color = if (rest > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showPad = false; splitCats.add(null); splitAmts.add(""); pickSplitIdx = splitCats.size - 1 }) {
                        Text(if (splitAmts.isEmpty()) "Split into several categories" else "+ Add another part")
                    }
                }
                PickerField("Wallet", wallet?.let { "${it.name} · ${it.currency}" } ?: "Choose wallet", wallet?.let { AppIcons.get(it.icon) } ?: Icons.Filled.AccountBalanceWallet,
                    wallet?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, onClick = { showPad = false; pickWallet = 1 })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField("Date", Dates.date(dateTime), Icons.Filled.CalendarToday, onClick = { showPad = false; pickDate = true }, modifier = Modifier.weight(1.4f))
                PickerField("Time", Dates.time(dateTime), Icons.Filled.Schedule, onClick = { showPad = false; pickTime = true }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
                interactionSource = noteSrc)
            if (type != TxType.TRANSFER) {
                OutlinedTextField(payee, { payee = it }, label = { Text(if (type == TxType.INCOME) "From (payer)" else "Paid to (payee)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    interactionSource = payeeSrc)
            }
            if (type != TxType.INCOME) {
                MoneyField(feeText, { feeText = it }, "Service fee (optional)", code)
            }
            // Photos
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.AddAPhoto, "Add photo", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                photos.toList().forEach { p ->
                    Box(Modifier.size(72.dp)) {
                        AsyncImage(model = Photos.file(ctx, p), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
                        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(RoundedCornerShape(11.dp))
                            .background(Color.Black.copy(alpha = 0.55f)).clickable { photos.remove(p) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        // ---- Keypad / save ----
        AnimatedVisibility(visible = showPad) {
            Keypad(
                accent = typeColor,
                onKey = { k -> expr = Calc.press(expr, k); error = null },
                onDone = {
                    if (Calc.hasOp(expr)) { evaluated?.let { expr = Money.toInput((it * 100).roundToLong().coerceAtLeast(0)) } }
                    else showPad = false
                },
                doneLabel = if (Calc.hasOp(expr)) "=" else "OK",
            )
        }
        if (!showPad) {
            Button(
                onClick = { save(false) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (pickWallet != 0) {
        WalletPickerSheet(
            bd, if (pickWallet == 1) walletId else toWalletId,
            title = if (type == TxType.TRANSFER) (if (pickWallet == 1) "Transfer from" else "Transfer to") else "Choose wallet",
            exclude = if (type == TxType.TRANSFER) (if (pickWallet == 1) toWalletId else walletId) else null,
            onDismiss = { pickWallet = 0 },
        ) { w -> if (pickWallet == 1) walletId = w?.id else toWalletId = w?.id; toAmountEdited = false }
    }
    if (pickCategory) {
        CategoryPickerSheet(bd, if (type == TxType.INCOME) CatType.INCOME else CatType.EXPENSE, categoryId,
            onDismiss = { pickCategory = false }, onManage = onManageCategories) { categoryId = it.id }
    }
    if (pickSplitIdx >= 0 && pickSplitIdx < splitCats.size) {
        val idx = pickSplitIdx
        CategoryPickerSheet(bd, if (type == TxType.INCOME) CatType.INCOME else CatType.EXPENSE, splitCats[idx],
            onDismiss = { pickSplitIdx = -1 }) { splitCats[idx] = it.id }
    }
    if (pickDate) DateDialog(dateTime, { pickDate = false }) { d ->
        val c = Dates.cal(dateTime); dateTime = Dates.combine(d, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
    if (pickTime) TimeDialog(dateTime, { pickTime = false }) { h, mi -> dateTime = Dates.combine(dateTime, h, mi) }
    templateName?.let { name ->
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { templateName = null },
            title = { Text("Save as template") },
            text = { OutlinedTextField(text, { text = it }, label = { Text("Template name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = text.trim().ifBlank { "Template" }
                    scope.launch {
                        Graph.dao.insertTemplate(Template(bookId = bd.bookId, name = n, type = type, amount = amountCents, walletId = walletId,
                            toWalletId = toWalletId, categoryId = categoryId, note = note.trim(), sortOrder = templates.size))
                        UiBus.toast.tryEmit("Template saved")
                    }
                    templateName = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { templateName = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun Keypad(accent: Color, onKey: (String) -> Unit, onDone: () -> Unit, doneLabel: String) {
    val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf(".", "0", "⌫", "+"),
    )
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(4f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k ->
                            val isOp = k in setOf("÷", "×", "−", "+")
                            Box(
                                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (isOp) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .clickable { onKey(k) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (k == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, "Delete")
                                else Text(k, fontSize = 22.sp, fontWeight = if (isOp) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isOp) accent else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .clickable { onKey("C") }, contentAlignment = Alignment.Center,
            ) { Text("C", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier.weight(3f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(accent).clickable { onDone() },
                contentAlignment = Alignment.Center,
            ) { Text(doneLabel, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
