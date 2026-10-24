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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.CategoryIcon
import com.spacecay.expenselog.ui.components.CurrencyPickerSheet
import com.spacecay.expenselog.ui.components.MoneyField
import com.spacecay.expenselog.ui.components.SectionTitle
import com.spacecay.expenselog.ui.components.SimpleTopBar
import com.spacecay.expenselog.ui.rememberBookData
import com.spacecay.expenselog.util.Currencies
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import kotlinx.coroutines.launch

/** What the rate dialog is doing. */
private sealed class RateEdit {
    data class Rate(val code: String, val isNew: Boolean) : RateEdit()
    data class NewMain(val code: String) : RateEdit()
}

@Composable
fun CurrenciesScreen(back: () -> Unit) {
    val bd = rememberBookData()
    val scope = rememberCoroutineScope()
    var addPicker by remember { mutableStateOf(false) }
    var mainPicker by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<RateEdit?>(null) }
    var deleteCode by remember { mutableStateOf<String?>(null) }
    var convAmount by remember { mutableStateOf("100") }
    var convFrom by remember { mutableStateOf<String?>(null) }
    var convPicker by remember { mutableStateOf(false) }

    val others = bd.rateList.filter { it.code != bd.main }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Currencies & rates", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { addPicker = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                Icon(Icons.Filled.Add, "Add currency")
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                AppCard {
                    Column {
                        Text("Main currency", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bd.main, style = MaterialTheme.typography.headlineSmall)
                                Text(Currencies.info(bd.main).name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(onClick = { mainPicker = true }) { Text("Change") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Totals, budgets and statistics are shown in the main currency. You can change it and every rate any time.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionTitle("Exchange rates") }
            if (others.isEmpty()) item {
                Text("No other currencies yet. Tap + to add one (for example USD) and set its rate.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            items(others, key = { it.code }) { r ->
                val usedBy = bd.wallets.count { it.currency == r.code }
                AppCard(onClick = { edit = RateEdit.Rate(r.code, false) }, padding = PaddingValues(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(Icons.Filled.CurrencyExchange, MaterialTheme.colorScheme.primary, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${r.code} · ${Currencies.info(r.code).name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("1 ${r.code} = ${Money.formatRate(r.rate)} ${bd.main}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text("1 ${bd.main} = ${Money.formatRate(1.0 / r.rate)} ${r.code} · updated ${Dates.relative(r.updatedAt)}" +
                                (if (usedBy > 0) " · $usedBy wallet${if (usedBy > 1) "s" else ""}" else ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (usedBy == 0) IconButton(onClick = { deleteCode = r.code }) { Icon(Icons.Filled.Delete, "Remove") }
                    }
                }
            }
            if (others.isNotEmpty()) {
                item { SectionTitle("Quick converter") }
                item {
                    val from = convFrom ?: bd.main
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { MoneyField(convAmount, { convAmount = it }, "Amount", from) }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(onClick = { convPicker = true }) { Text(from) }
                            }
                            val cents = Money.parse(convAmount) ?: 0L
                            (listOf(bd.main) + others.map { it.code }).filter { it != from }.forEach { code ->
                                Row {
                                    Text(code, Modifier.width(64.dp), fontWeight = FontWeight.SemiBold)
                                    Text(Money.format(bd.rates.convert(cents, from, code), code))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addPicker) CurrencyPickerSheet(exclude = bd.rateList.map { it.code }.toSet() + bd.main, onDismiss = { addPicker = false }) {
        edit = RateEdit.Rate(it.code, true)
    }
    if (mainPicker) CurrencyPickerSheet(exclude = setOf(bd.main), onDismiss = { mainPicker = false }) {
        edit = RateEdit.NewMain(it.code)
    }
    if (convPicker) CurrencyPickerSheet(onDismiss = { convPicker = false }) { convFrom = it.code }

    when (val e = edit) {
        is RateEdit.Rate -> RateDialog(bd, e.code, if (e.isNew) "Add ${e.code}" else "Edit ${e.code} rate", onDismiss = { edit = null }) { rate ->
            scope.launch { Graph.repo.setRate(bd.bookId, e.code, rate) }
            edit = null
        }
        is RateEdit.NewMain -> {
            val known = bd.rateList.firstOrNull { it.code == e.code }?.rate
            var text by remember(e.code) { mutableStateOf(known?.let { Money.formatRate(it) } ?: "") }
            AlertDialog(
                onDismissRequest = { edit = null },
                title = { Text("Switch main currency to ${e.code}") },
                text = {
                    Column {
                        Text("How many ${bd.main} is 1 ${e.code}? All other rates are recalculated so your totals stay correct.",
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(text, { text = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true,
                            label = { Text("1 ${e.code} = ? ${bd.main}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val r = text.toDoubleOrNull()
                        if (r != null && r > 0) {
                            scope.launch { Graph.repo.changeMainCurrency(bd.bookId, e.code, r); Graph.repo.refreshWidget() }
                            edit = null
                        }
                    }) { Text("Switch") }
                },
                dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } },
            )
        }
        null -> {}
    }
    deleteCode?.let { code ->
        AlertDialog(
            onDismissRequest = { deleteCode = null },
            title = { Text("Remove $code?") },
            text = { Text("No wallet uses $code. You can add it back later.") },
            confirmButton = { TextButton(onClick = { scope.launch { Graph.dao.deleteRate(bd.bookId, code) }; deleteCode = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleteCode = null }) { Text("Cancel") } },
        )
    }
}

/**
 * Edit a rate in either direction: "1 USD = x ETB" or "1 ETB = x USD".
 */
@Composable
fun RateDialog(bd: BookData, code: String, title: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val current = bd.rateList.firstOrNull { it.code == code }?.rate
    var inverse by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(current?.let { Money.formatRate(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (!inverse) "1 $code =" else "1 ${bd.main} =", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(text, { text = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true,
                        suffix = { Text(if (!inverse) bd.main else code) }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                TextButton(onClick = {
                    val v = text.toDoubleOrNull()
                    inverse = !inverse
                    if (v != null && v > 0) text = Money.formatRate(1.0 / v)
                }) {
                    Icon(Icons.Filled.SwapHoriz, null); Spacer(Modifier.width(6.dp)); Text("Enter the other way round")
                }
                val v = text.toDoubleOrNull()
                if (v != null && v > 0) {
                    val rate = if (inverse) 1.0 / v else v
                    Text("100 $code = ${Money.format(Money.convert(10000, rate), bd.main)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.toDoubleOrNull()
                if (v != null && v > 0) onSave(if (inverse) 1.0 / v else v)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
