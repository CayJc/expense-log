@file:OptIn(ExperimentalMaterial3Api::class)

package com.spacecay.expenselog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacecay.expenselog.data.Category
import com.spacecay.expenselog.data.Wallet
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.util.AppIcons
import com.spacecay.expenselog.util.Currencies
import com.spacecay.expenselog.util.CurrencyInfo
import com.spacecay.expenselog.util.Money

@Composable
fun WalletPickerSheet(
    bd: BookData,
    selected: Long?,
    title: String = "Choose wallet",
    exclude: Long? = null,
    allowNone: Boolean = false,
    onDismiss: () -> Unit,
    onPick: (Wallet?) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                if (allowNone) item {
                    SheetRow(null, Color.Gray, "None", null, selected == null) { onPick(null); onDismiss() }
                }
                items(bd.wallets.filter { !it.archived && it.id != exclude }) { w ->
                    SheetRow(AppIcons.get(w.icon), Color(w.color), w.name, Money.format(bd.balance(w), w.currency), w.id == selected) {
                        onPick(w); onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, color: Color, title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) SolidIcon(icon, color, 40.dp) else Box(Modifier.width(40.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Grid of top-level categories; tapping one with subcategories reveals them.
 */
@Composable
fun CategoryPickerSheet(
    bd: BookData,
    type: Int,
    selected: Long?,
    onDismiss: () -> Unit,
    onManage: (() -> Unit)? = null,
    onPick: (Category) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var parent by remember { mutableStateOf<Category?>(null) }
    val tops = bd.categories.filter { it.type == type && it.parentId == null && !it.archived }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(parent?.name ?: "Choose category", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (parent != null) TextButton(onClick = { parent = null }) { Text("Back") }
                else if (onManage != null) TextButton(onClick = { onDismiss(); onManage() }) { Text("Manage") }
            }
            val list: List<Category> = parent?.let { p -> listOf(p) + bd.categories.filter { it.parentId == p.id && !it.archived } } ?: tops
            LazyVerticalGrid(GridCells.Fixed(4), contentPadding = PaddingValues(12.dp), modifier = Modifier.heightIn(max = 480.dp)) {
                items(list, key = { it.id }) { c ->
                    val hasSubs = parent == null && bd.categories.any { it.parentId == c.id && !it.archived }
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (c.id == selected) Color(c.color).copy(alpha = 0.12f) else Color.Transparent)
                            .clickable {
                                if (hasSubs) parent = c else { onPick(c); onDismiss() }
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SolidIcon(AppIcons.get(c.icon), Color(c.color), 46.dp)
                        Spacer(Modifier.padding(top = 6.dp))
                        Text(
                            if (parent != null && c.id == parent?.id) "General" else c.name,
                            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (hasSubs) Text("•••", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun CurrencyPickerSheet(
    exclude: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onPick: (CurrencyInfo) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Choose currency", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search or type a code") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            val list = Currencies.all.filter { it.code !in exclude && (q.isBlank() || it.code.contains(q, true) || it.name.contains(q, true)) }
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                val custom = q.trim().uppercase()
                if (custom.length in 2..6 && list.none { it.code == custom } && custom !in exclude) item {
                    Row(Modifier.fillMaxWidth().clickable { onPick(CurrencyInfo(custom, custom, custom)); onDismiss() }.padding(20.dp, 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(12.dp)); Text("Use custom code \"$custom\"")
                    }
                }
                items(list, key = { it.code }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(c); onDismiss() }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(c.code, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(64.dp))
                        Text(c.name, modifier = Modifier.weight(1f))
                        Text(c.symbol, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
