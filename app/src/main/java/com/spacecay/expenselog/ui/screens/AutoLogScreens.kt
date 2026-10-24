@file:OptIn(ExperimentalMaterial3Api::class)

package com.spacecay.expenselog.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.autolog.AutoLogger
import com.spacecay.expenselog.autolog.PaymentParser
import com.spacecay.expenselog.autolog.ScreenshotScanner
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.Prefs
import com.spacecay.expenselog.data.TxSource
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.ui.UiBus
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.PickerField
import com.spacecay.expenselog.ui.components.SectionTitle
import com.spacecay.expenselog.ui.components.SettingRow
import com.spacecay.expenselog.ui.components.SimpleTopBar
import com.spacecay.expenselog.ui.components.TxRow
import com.spacecay.expenselog.ui.components.WalletPickerSheet
import com.spacecay.expenselog.ui.rememberBookData
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.AppIcons
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.util.PeriodKind
import kotlinx.coroutines.launch

@Composable
fun AutoLogScreen(back: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val bd = rememberBookData()
    val m = LocalMoney.current
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun listenerOn() = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    var notifAccess by remember { mutableStateOf(listenerOn()) }
    var shotPerm by remember { mutableStateOf(ScreenshotScanner.hasPermission(ctx)) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { notifAccess = listenerOn(); shotPerm = ScreenshotScanner.hasPermission(ctx) }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    var notifOn by remember { mutableStateOf(prefs.autoLogNotifications) }
    var shotsOn by remember { mutableStateOf(prefs.autoLogScreenshots) }
    var fees by remember { mutableStateOf(prefs.logFees) }
    var watched by remember { mutableStateOf(prefs.watchedPackages) }
    var lastScan by remember { mutableLongStateOf(prefs.lastScreenshotScan) }
    var scanning by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf(0) } // 1 telebirr, 2 cbe
    var tele by remember { mutableLongStateOf(prefs.telebirrWalletId) }
    var cbe by remember { mutableLongStateOf(prefs.cbeWalletId) }
    var test by remember { mutableStateOf("") }
    val rules by remember(bd.bookId) { Graph.dao.observeRules(bd.bookId) }.collectAsStateWithLifecycle(emptyList())

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> shotPerm = ok }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { SimpleTopBar("Auto-log", back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Payments are logged automatically and marked for review so you can confirm the category.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))

            SectionTitle("From notifications (telebirr)")
            AppCard(padding = PaddingValues(4.dp)) {
                Column {
                    SettingRow(
                        if (notifAccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        if (notifAccess) "Notification access is on" else "Allow notification access",
                        if (notifAccess) "Expense Log can read payment notifications" else "Needed to read telebirr payment notifications",
                        iconColor = if (notifAccess) m.income else m.expense,
                        onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    )
                    SettingRow(Icons.Filled.NotificationsActive, "Log from notifications", trailing = {
                        Switch(notifOn, { prefs.autoLogNotifications = it; notifOn = it })
                    })
                }
            }
            SectionTitle("Apps to read")
            AppCard(padding = PaddingValues(4.dp)) {
                Column {
                    val all = (Prefs.DEFAULT_PACKAGES + Prefs.SMS_PACKAGES + prefs.seenPackages + watched).toSortedSet()
                    all.forEach { pkg ->
                        val label = remember(pkg) {
                            try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { null }
                        }
                        val installed = label != null
                        if (installed || pkg in watched) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(pkg in watched, { on ->
                                    watched = if (on) watched + pkg else watched - pkg
                                    prefs.watchedPackages = watched
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(label ?: pkg, style = MaterialTheme.typography.bodyLarge)
                                    Text(pkg + if (pkg in Prefs.SMS_PACKAGES) " · SMS" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Text("Apps appear here after they post a notification. Only payment messages are logged.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                }
            }

            SectionTitle("From screenshots (CBE, telebirr packages)")
            AppCard(padding = PaddingValues(4.dp)) {
                Column {
                    if (!shotPerm) SettingRow(Icons.Filled.ErrorOutline, "Allow access to photos", "Needed to read new screenshots", iconColor = m.expense,
                        onClick = { permLauncher.launch(ScreenshotScanner.permission) })
                    SettingRow(Icons.Filled.PhotoLibrary, "Read new screenshots", "Take a screenshot of the success screen — it's logged within minutes",
                        trailing = { Switch(shotsOn, { prefs.autoLogScreenshots = it; shotsOn = it }) })
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (lastScan > 0) "Last checked ${Dates.dateTime(lastScan)}" else "Not checked yet", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        FilledTonalButton(enabled = shotPerm && !scanning, onClick = {
                            scanning = true
                            scope.launch {
                                val r = try { ScreenshotScanner.scan(ctx, force = true) } catch (e: Exception) { ScreenshotScanner.Result(0, 0) }
                                scanning = false; lastScan = prefs.lastScreenshotScan
                                UiBus.toast.tryEmit(if (r.logged > 0) "Logged ${r.logged} payment(s)" else "No new payment screenshots")
                            }
                        }) { Text(if (scanning) "Checking…" else "Check now") }
                    }
                    Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Share, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Or share any receipt screenshot or SMS to Expense Log to add it straight away.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SectionTitle("Which wallet")
            AppCard(padding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val tw = bd.walletMap[tele]; val cw = bd.walletMap[cbe]
                    PickerField("telebirr payments go to", tw?.name ?: "Auto", tw?.let { AppIcons.get(it.icon) } ?: Icons.Filled.AccountBalanceWallet,
                        tw?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, onClick = { pickFor = 1 })
                    PickerField("CBE payments go to", cw?.name ?: "Auto", cw?.let { AppIcons.get(it.icon) } ?: Icons.Filled.AccountBalanceWallet,
                        cw?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary, onClick = { pickFor = 2 })
                }
            }
            AppCard(padding = PaddingValues(4.dp)) {
                SettingRow(Icons.Filled.Receipt, "Include service fees", "Adds the fee to the payment amount", trailing = {
                    Switch(fees, { prefs.logFees = it; fees = it })
                })
            }

            SectionTitle("Learned payees")
            AppCard(padding = PaddingValues(4.dp)) {
                Column {
                    if (rules.isEmpty()) Text("When you set a category on an auto-logged payment, the payee is remembered here.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    rules.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.keyword, style = MaterialTheme.typography.bodyLarge)
                                Text("→ " + bd.categoryLabel(r.categoryId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { scope.launch { Graph.dao.deleteRule(r.id) } }) { Icon(Icons.Filled.Delete, "Forget") }
                        }
                    }
                }
            }

            SectionTitle("Test a message")
            AppCard(padding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(test, { test = it }, label = { Text("Paste a telebirr / CBE message") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    if (test.isNotBlank()) {
                        val p = PaymentParser.parse(test)
                        if (p == null) Text("Couldn't read a payment from this text.", color = m.expense)
                        else {
                            Text(AutoLogger.describe(p), style = MaterialTheme.typography.titleSmall, color = if (p.type == TxType.INCOME) m.income else m.expense)
                            Text(listOfNotNull(
                                "Source: ${p.provider.name.lowercase()}",
                                p.fee.takeIf { it > 0 }?.let { "fee ${Money.plain(it)}" },
                                p.ref?.let { "ref $it" },
                                p.balance?.let { "balance ${Money.plain(it)}" },
                                p.time?.let { Dates.dateTime(it) },
                            ).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = {
                                scope.launch { UiBus.prefill.value = AutoLogger.prefillFromText(test); go("txedit?id=0&type=${p.type}") }
                            }) { Text("Add this transaction") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickFor != 0) WalletPickerSheet(bd, if (pickFor == 1) tele else cbe, onDismiss = { pickFor = 0 }) { w ->
        val id = w?.id ?: 0L
        if (pickFor == 1) { prefs.telebirrWalletId = id; tele = id } else { prefs.cbeWalletId = id; cbe = id }
    }
}

/** The v1 "text log" — today's transactions as plain text you can copy or share at night. */
@Composable
fun DailyLogScreen(back: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val bd = rememberBookData()
    val m = LocalMoney.current
    val clipboard = LocalClipboardManager.current
    var day by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val r = Dates.day(day)
    val txns by remember(bd.bookId, r) { Graph.dao.observeTxnsBetween(bd.bookId, r.start, r.end) }.collectAsStateWithLifecycle(emptyList())
    val sorted = txns.sortedBy { it.dateTime }
    val text = buildString {
        appendLine("Expense log — ${Dates.fmt("EEE d MMM yyyy", day)}")
        appendLine()
        sorted.forEach { t ->
            val d = bd.display(t)
            val sign = when (t.type) { TxType.INCOME -> "+"; TxType.EXPENSE -> "-"; else -> "↔" }
            val who = listOf(t.payee, t.note).filter { it.isNotBlank() }.joinToString(", ")
            appendLine("${Dates.time(t.dateTime)}  $sign${Money.plain(t.amount + (if (t.type == TxType.EXPENSE) t.fee else 0L))} ${bd.currencyOf(t.walletId)}  ${d.title}${if (who.isNotBlank()) " ($who)" else ""} [${bd.walletMap[t.walletId]?.name ?: ""}]")
        }
        appendLine()
        appendLine("Total spent: ${Money.format(Stats.expense(txns, bd), bd.main)}")
        val inc = Stats.income(txns, bd)
        if (inc > 0) appendLine("Total received: ${Money.format(inc, bd.main)}")
        appendLine("Transactions: ${txns.size}")
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar("Daily log", back) {
                IconButton(onClick = { clipboard.setText(AnnotatedString(text)); UiBus.toast.tryEmit("Copied") }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                IconButton(onClick = {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share log")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Icon(Icons.Filled.Share, "Share") }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                com.spacecay.expenselog.ui.components.PeriodSwitcher(Dates.dayHeader(day) + " · " + Dates.date(day),
                    { day = Dates.shift(PeriodKind.DAY, day, -1) }, { day = Dates.shift(PeriodKind.DAY, day, 1) }, Modifier.padding(horizontal = 8.dp))
            }
            item {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    Row {
                        SmallStat("Spent", Money.format(Stats.expense(txns, bd), bd.main), m.expense, Modifier.weight(1f))
                        SmallStat("Received", Money.format(Stats.income(txns, bd), bd.main), m.income, Modifier.weight(1f))
                    }
                }
            }
            item {
                AppCard(Modifier.padding(16.dp)) {
                    SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                AppCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(4.dp)) {
                    Column { sorted.forEach { t -> TxRow(t, bd) { go("tx/${t.id}") } } }
                }
            }
        }
    }
}
