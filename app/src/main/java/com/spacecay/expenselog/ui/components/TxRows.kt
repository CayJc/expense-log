package com.spacecay.expenselog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacecay.expenselog.data.TxSource
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.data.Txn
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TxRow(t: Txn, bd: BookData, hidden: Boolean = false, showTime: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val m = LocalMoney.current
    val d = bd.display(t)
    val code = bd.currencyOf(t.walletId)
    val (color, signed) = when (t.type) {
        TxType.INCOME -> m.income to t.amount
        TxType.EXPENSE -> m.expense to -t.amount
        else -> m.transfer to t.amount
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CategoryIcon(d.icon, d.color, 42.dp)
            if (!t.reviewed) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).align(Alignment.TopEnd))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                val badge = when (t.source) {
                    TxSource.NOTIFICATION, TxSource.SHARED_TEXT -> Icons.Filled.AutoAwesome
                    TxSource.SCREENSHOT -> Icons.Filled.PhotoCamera
                    TxSource.RECURRING -> Icons.Filled.Repeat
                    else -> null
                }
                if (badge != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(badge, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                }
            }
            val sub = listOf(d.subtitle, if (showTime) Dates.time(t.dateTime) else "").filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (hidden) "••••" else Money.format(signed, code, showSign = t.type == TxType.INCOME),
                color = color, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
            )
            if (t.type == TxType.TRANSFER && t.toAmount != null && t.toAmount != t.amount) {
                Text("→ " + Money.format(t.toAmount, bd.currencyOf(t.toWalletId)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (t.fee > 0) {
                Text("fee " + Money.plain(t.fee), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (code != bd.main && !hidden) {
                Text("≈ " + Money.format(bd.mainAmount(t), bd.main), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Adds day-grouped cards for transactions into a LazyColumn. */
fun LazyListScope.dayGroupedTxns(
    txns: List<Txn>,
    bd: BookData,
    hidden: Boolean,
    onClick: (Txn) -> Unit,
) {
    val groups = txns.groupBy { Dates.startOfDay(it.dateTime) }.toSortedMap(compareByDescending { it })
    groups.forEach { (day, list) ->
        item(key = "d$day") {
            val m = LocalMoney.current
            val eth = com.spacecay.expenselog.data.Graph.prefs.ethCal.collectAsState().value == 1
            val inc = Stats.income(list, bd)
            val exp = Stats.expense(list, bd)
            AppCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.fmt("dd", day), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Dates.dayHeader(day), style = MaterialTheme.typography.labelLarge)
                            Text(Dates.fmt("MMMM yyyy", day) + (if (eth) " · " + com.spacecay.expenselog.util.EthiopianCalendar.format(day, false) else ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                            if (inc > 0) Text(if (hidden) "••••" else "+" + Money.plain(inc), color = m.income, style = MaterialTheme.typography.labelMedium)
                            if (exp > 0) Text(if (hidden) "••••" else "-" + Money.plain(exp), color = m.expense, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    list.forEach { t -> TxRow(t, bd, hidden) { onClick(t) } }
                }
            }
        }
    }
}
