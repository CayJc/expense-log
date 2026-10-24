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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.DebtType
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.Wallet
import com.spacecay.expenselog.data.WalletType
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.SectionTitle
import com.spacecay.expenselog.ui.components.SolidIcon
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.AppIcons
import com.spacecay.expenselog.util.Money

@Composable
fun WalletsTab(bd: BookData, hidden: Boolean, go: (String) -> Unit) {
    val m = LocalMoney.current
    val debts by remember(bd.bookId) { Graph.dao.observeDebts(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val debtEntries by remember { Graph.dao.observeAllDebtEntries() }.collectAsStateWithLifecycle(emptyList())
    val goals by remember(bd.bookId) { Graph.dao.observeGoals(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    val goalEntries by remember { Graph.dao.observeAllGoalEntries() }.collectAsStateWithLifecycle(emptyList())
    val budgets by remember(bd.bookId) { Graph.dao.observeBudgets(bd.bookId) }.collectAsStateWithLifecycle(emptyList())

    val active = bd.wallets.filter { !it.archived }
    val assets = active.filter { !it.excludeFromTotal }.map { bd.rates.toMain(bd.balance(it), it.currency) }.filter { it > 0 }.sum()
    val liabilities = active.filter { !it.excludeFromTotal }.map { bd.rates.toMain(bd.balance(it), it.currency) }.filter { it < 0 }.sum()

    fun debtLeft(type: Int): Long = debts.filter { it.type == type && !it.closed }.sumOf { d ->
        val es = debtEntries.filter { it.debtId == d.id }
        val left = d.amount - es.sumOf { it.amount }
        bd.rates.toMain(left.coerceAtLeast(0), d.currency)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(m.heroStart, m.heroEnd))).padding(20.dp),
            ) {
                Column {
                    Text("Net worth", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text(if (hidden) "••••••" else Money.format(bd.total, bd.main), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Assets", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                            Text(if (hidden) "••••" else Money.compact(assets), color = Color(0xFFB9F5D6), style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Liabilities", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                            Text(if (hidden) "••••" else Money.compact(liabilities), color = Color(0xFFFFC9CB), style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Wallets", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                            Text(active.size.toString(), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        item { SectionTitle("My wallets", Modifier.padding(horizontal = 16.dp), action = "Manage", onAction = { go("wallets/manage") }) }
        item {
            AppCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
                Column { active.forEach { w -> WalletRow(w, bd, hidden) { go("wallet/${w.id}") } } }
            }
        }
        item { SectionTitle("Planning", Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanTile("Budgets", "${budgets.size} active", Icons.Filled.PieChart, Color(0xFF7E57C2), Modifier.weight(1f)) { go("budgets") }
                PlanTile("Goals", "${goals.count { !it.reached }} ongoing", Icons.Filled.Flag, Color(0xFF26A69A), Modifier.weight(1f)) { go("goals") }
            }
        }
        item {
            AppCard(Modifier.padding(16.dp), onClick = { go("debts") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SolidIcon(Icons.Filled.Handshake, Color(0xFFFF7043), 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Debts & loans", style = MaterialTheme.typography.titleSmall)
                        Text("I owe ${if (hidden) "••••" else Money.format(debtLeft(DebtType.PAYABLE), bd.main)}",
                            style = MaterialTheme.typography.bodySmall, color = m.expense)
                        Text("Owed to me ${if (hidden) "••••" else Money.format(debtLeft(DebtType.RECEIVABLE), bd.main)}",
                            style = MaterialTheme.typography.bodySmall, color = m.income)
                    }
                }
            }
        }
        val savedTotal = goals.sumOf { g -> bd.rates.toMain(goalEntries.filter { it.goalId == g.id }.sumOf { it.amount }, g.currency) }
        if (savedTotal > 0) item {
            Text("Saved towards goals: ${if (hidden) "••••" else Money.format(savedTotal, bd.main)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
fun WalletRow(w: Wallet, bd: BookData, hidden: Boolean, onClick: () -> Unit) {
    val m = LocalMoney.current
    val bal = bd.balance(w)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickableNoRipple(onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SolidIcon(AppIcons.get(w.icon), Color(w.color), 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(w.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                WalletType.labels.getOrElse(w.type) { "Wallet" } + (if (w.excludeFromTotal) " · not in total" else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (hidden) "••••" else Money.format(bal, w.currency), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = if (bal < 0) m.expense else MaterialTheme.colorScheme.onSurface)
            if (w.currency != bd.main && !hidden) Text("≈ " + Money.format(bd.rates.toMain(bal, w.currency), bd.main), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (w.type == WalletType.CREDIT && w.creditLimit > 0 && !hidden)
                Text("Available " + Money.format(w.creditLimit + bal, w.currency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanTile(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) {
        AppCard(onClick = onClick) {
            Column {
                SolidIcon(icon, color, 38.dp)
                Spacer(Modifier.height(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
