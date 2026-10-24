@file:OptIn(ExperimentalMaterial3Api::class)

package com.spacecay.expenselog.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.Book
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.backup.Backup
import com.spacecay.expenselog.backup.Export
import com.spacecay.expenselog.ui.UiBus
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.ConfirmDialog
import com.spacecay.expenselog.ui.components.CurrencyPickerSheet
import com.spacecay.expenselog.ui.components.DateDialog
import com.spacecay.expenselog.ui.components.PickerField
import com.spacecay.expenselog.ui.components.Pill
import com.spacecay.expenselog.ui.components.SectionTitle
import com.spacecay.expenselog.ui.components.SettingRow
import com.spacecay.expenselog.ui.components.SimpleTopBar
import com.spacecay.expenselog.ui.components.TimeDialog
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Range
import com.spacecay.expenselog.work.Scheduler
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun SettingsScreen(back: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = Graph.prefs
    val theme by prefs.theme.collectAsStateWithLifecycle()
    val hidden by prefs.hideAmounts.collectAsStateWithLifecycle()
    var lockOn by remember { mutableStateOf(prefs.lockEnabled) }
    var bio by remember { mutableStateOf(prefs.biometric) }
    var setPin by remember { mutableStateOf(false) }
    var firstDay by remember { mutableIntStateOf(prefs.firstDayOfWeek) }
    var monthStart by remember { mutableIntStateOf(prefs.monthStartDay) }
    var daily by remember { mutableStateOf(prefs.dailySummary) }
    var dailyAt by remember { mutableIntStateOf(prefs.dailySummaryMinutes) }
    var pickTime by remember { mutableStateOf(false) }
    var pickMonthStart by remember { mutableStateOf(false) }
    val activity = ctx as? FragmentActivity
    val canBio = activity != null && biometricAvailable(activity)

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { SimpleTopBar("Settings", back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("Appearance")
            AppCard(padding = PaddingValues(12.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  Theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("System", theme == 0, { prefs.setTheme(0) })
                        Pill("Light", theme == 1, { prefs.setTheme(1) })
                        Pill("Dark", theme == 2, { prefs.setTheme(2) })
                    }
                }
            }
            AppCard(padding = PaddingValues(4.dp)) {
                SettingRow(Icons.Filled.Visibility, "Hide amounts", "Show •••• instead of numbers (quick toggle on the home screen too)",
                    trailing = { Switch(hidden, { prefs.setHideAmounts(it) }) })
            }

            SectionTitle("Security")
            AppCard(padding = PaddingValues(4.dp)) {
                Column {
                    SettingRow(Icons.Filled.Lock, "App lock (PIN)", if (lockOn) "On" else "Off", trailing = {
                        Switch(lockOn, { on -> if (on) setPin = true else { prefs.savePin(null); prefs.biometric = false; lockOn = false; bio = false } })
                    })
                    if (lockOn) SettingRow(Icons.Filled.Edit, "Change PIN", onClick = { setPin = true })
                    if (lockOn && canBio) SettingRow(Icons.Filled.Fingerprint, "Unlock with fingerprint", trailing = {
                        Switch(bio, { prefs.biometric = it; bio = it })
                    })
                }
            }

            SectionTitle("Dates")
            AppCard(padding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("First day of week", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Calendar.MONDAY to "Monday", Calendar.SUNDAY to "Sunday", Calendar.SATURDAY to "Saturday").forEach { (v, l) ->
                            Pill(l, firstDay == v, { firstDay = v; prefs.firstDayOfWeek = v; Dates.firstDayOfWeek = v })
                        }
                    }
                }
            }
            AppCard(padding = PaddingValues(4.dp)) {
                SettingRow(Icons.Filled.CalendarMonth, "Month starts on day $monthStart", "Use your salary day so monthly totals and budgets line up",
                    onClick = { pickMonthStart = true })
            }

            SectionTitle("Notifications")
            AppCard(padding = PaddingValues(4.dp)) {
                Column {
                    SettingRow(Icons.Filled.Notifications, "Nightly summary", "What you spent today, every evening", trailing = {
                        Switch(daily, { prefs.dailySummary = it; daily = it; Scheduler.scheduleDaily(ctx) })
                    })
                    if (daily) SettingRow(Icons.Filled.Today, "Summary time", "%02d:%02d".format(dailyAt / 60, dailyAt % 60), onClick = { pickTime = true })
                }
            }

            SectionTitle("Data")
            AppCard(padding = PaddingValues(4.dp)) {
                Column {
                    SettingRow(Icons.Filled.AutoAwesome, "Auto-log (telebirr / CBE)", onClick = { go("autolog") })
                    SettingRow(Icons.Filled.CurrencyExchange, "Currencies & rates", onClick = { go("currencies") })
                    SettingRow(Icons.Filled.Category, "Categories", onClick = { go("categories") })
                    SettingRow(Icons.AutoMirrored.Filled.MenuBook, "Books", "Separate ledgers, e.g. personal and business", onClick = { go("books") })
                    SettingRow(Icons.Filled.Backup, "Backup & export", onClick = { go("backup") })
                }
            }
            AppCard(padding = PaddingValues(4.dp)) {
                SettingRow(Icons.Filled.Info, "Expense Log 3.0", "Every feature unlocked · no ads · data stays on your phone")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (setPin) {
        Dialog(onDismissRequest = { setPin = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            SetPinScreen { pin ->
                if (pin != null) { prefs.savePin(pin); lockOn = true; UiBus.toast.tryEmit("PIN set") }
                setPin = false
            }
        }
    }
    if (pickTime) TimeDialog(Dates.combine(System.currentTimeMillis(), dailyAt / 60, dailyAt % 60), { pickTime = false }) { h, m ->
        dailyAt = h * 60 + m; prefs.dailySummaryMinutes = dailyAt; Scheduler.scheduleDaily(ctx)
    }
    if (pickMonthStart) {
        AlertDialog(
            onDismissRequest = { pickMonthStart = false },
            title = { Text("Month starts on") },
            text = {
                LazyColumn(Modifier.height(320.dp)) {
                    items((1..28).toList()) { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                monthStart = d; prefs.monthStartDay = d; Dates.monthStartDay = d; pickMonthStart = false
                            }) { Text("Day $d") }
                            if (d == monthStart) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
fun BooksScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val books by remember { Graph.dao.observeBooks() }.collectAsStateWithLifecycle(emptyList())
    val current by Graph.prefs.bookId.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Book?>(null) }
    var delete by remember { mutableStateOf<Book?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Books", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { Icon(Icons.Filled.Add, "Add") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Each book has its own wallets, categories, budgets and main currency. Switch from the title on the home screen.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(books, key = { it.id }) { b ->
                AppCard(padding = PaddingValues(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f)) {
                            SettingRow(Icons.AutoMirrored.Filled.MenuBook, b.name, "Main currency ${b.mainCurrency}" + if (b.id == current) " · in use" else "",
                                onClick = { Graph.prefs.setBook(b.id) },
                                trailing = { if (b.id == current) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) })
                        }
                        IconButton(onClick = { rename = b }) { Icon(Icons.Filled.Edit, "Rename") }
                        if (books.size > 1) IconButton(onClick = { delete = b }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                }
            }
        }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var cur by remember { mutableStateOf("ETB") }
        var pick by remember { mutableStateOf(false) }
        var wallets by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("New book") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    PickerField("Main currency", cur, null, onClick = { pick = true })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(wallets, { wallets = it }); Text("  Add Cash / telebirr / CBE wallets")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) scope.launch { val id = Graph.repo.createBook(n, cur, wallets); Graph.prefs.setBook(id) }
                    adding = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
        if (pick) CurrencyPickerSheet(onDismiss = { pick = false }) { cur = it.code }
    }
    rename?.let { b ->
        var name by remember(b.id) { mutableStateOf(b.name) }
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Rename book") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { scope.launch { Graph.dao.updateBook(b.copy(name = name.trim().ifBlank { b.name })) }; rename = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } },
        )
    }
    delete?.let { b ->
        ConfirmDialog("Delete \"${b.name}\"?", "Everything in this book — wallets, transactions, budgets — is deleted.", onDismiss = { delete = null }) {
            scope.launch {
                if (b.id == current) books.firstOrNull { it.id != b.id }?.let { Graph.prefs.setBook(it.id) }
                Graph.repo.deleteBook(b.id)
            }
        }
    }
}

@Composable
fun BackupScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = Graph.prefs
    var folder by remember { mutableStateOf(prefs.backupFolder) }
    var auto by remember { mutableIntStateOf(prefs.autoBackup) }
    var last by remember { mutableLongStateOf(prefs.lastBackup) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var from by remember { mutableLongStateOf(Dates.month(System.currentTimeMillis()).start) }
    var to by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun toast(s: String) { UiBus.toast.tryEmit(s) }
    val range = Range(Dates.startOfDay(from), Dates.day(to).end)

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try { Backup.writeTo(ctx, uri); last = prefs.lastBackup; toast("Backup saved") } catch (e: Exception) { toast("Backup failed: ${e.message}") }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.backupFolder = uri.toString(); folder = uri.toString()
            if (auto == 0) { auto = 1; prefs.autoBackup = 1 }
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { try { val n = Export.csv(ctx, uri, range); toast("Exported $n transactions") } catch (e: Exception) { toast("Export failed") } }
    }
    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) scope.launch { try { val n = Export.xlsx(ctx, uri, range); toast("Exported $n transactions") } catch (e: Exception) { toast("Export failed") } }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { SimpleTopBar("Backup & export", back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("Backup")
            AppCard(padding = PaddingValues(4.dp)) {
                Column {
                    SettingRow(Icons.Filled.CloudUpload, "Back up now", if (last > 0) "Last backup ${Dates.dateTime(last)}" else "Save a file — pick Google Drive to keep it in the cloud",
                        onClick = { saveBackup.launch(Backup.fileName()) })
                    SettingRow(Icons.Filled.Restore, "Restore from backup", "Replaces everything in the app", onClick = { openBackup.launch(arrayOf("application/json", "*/*")) })
                }
            }
            SectionTitle("Automatic backup")
            AppCard(padding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingRow(Icons.Filled.Folder, "Backup folder", folder?.let { Uri.parse(it).lastPathSegment ?: it } ?: "Not set — tap to choose",
                        onClick = { pickFolder.launch(null) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Off", "Daily", "Weekly").forEachIndexed { i, l -> Pill(l, auto == i, { auto = i; prefs.autoBackup = i }) }
                    }
                    if (folder != null) TextButton(onClick = {
                        busy = true
                        scope.launch { val ok = Backup.backupToFolder(ctx); last = prefs.lastBackup; busy = false; toast(if (ok) "Backed up to folder" else "Couldn't write to folder") }
                    }, enabled = !busy) { Text("Back up to folder now") }
                    Text("Keeps the 10 newest backups. Photos aren't included.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionTitle("Export transactions")
            AppCard(padding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerField("From", Dates.date(from), Icons.Filled.CalendarMonth, onClick = { pickFrom = true }, modifier = Modifier.weight(1f))
                        PickerField("To", Dates.date(to), Icons.Filled.CalendarMonth, onClick = { pickTo = true }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("This month", false, { from = Dates.month(System.currentTimeMillis()).start; to = System.currentTimeMillis() })
                        Pill("This year", false, { from = Dates.year(System.currentTimeMillis()).start; to = System.currentTimeMillis() })
                        Pill("All", false, { scope.launch { from = Graph.dao.firstTxnDate(prefs.bookId.value) ?: from; to = System.currentTimeMillis() } })
                    }
                    SettingRow(Icons.Filled.TableChart, "Export to Excel (.xlsx)", onClick = { xlsx.launch("ExpenseLog-${Dates.fmt("yyyyMMdd", from)}-${Dates.fmt("yyyyMMdd", to)}.xlsx") })
                    SettingRow(Icons.Filled.Download, "Export to CSV", onClick = { csv.launch("ExpenseLog-${Dates.fmt("yyyyMMdd", from)}-${Dates.fmt("yyyyMMdd", to)}.csv") })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickFrom) DateDialog(from, { pickFrom = false }) { from = it }
    if (pickTo) DateDialog(to, { pickTo = false }) { to = it }
    restoreUri?.let { uri ->
        ConfirmDialog("Restore backup?", "All current data is replaced by the backup.", confirm = "Restore", onDismiss = { restoreUri = null }) {
            scope.launch {
                try { Backup.readFrom(ctx, uri); toast("Restored") } catch (e: Exception) { toast("Restore failed: ${e.message}") }
            }
        }
    }
}
