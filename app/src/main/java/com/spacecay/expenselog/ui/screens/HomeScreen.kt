@file:OptIn(ExperimentalMaterial3Api::class)

package com.spacecay.expenselog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.ui.BookData
import com.spacecay.expenselog.ui.Stats
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.EmptyState
import com.spacecay.expenselog.ui.components.PeriodSwitcher
import com.spacecay.expenselog.ui.components.dayGroupedTxns
import com.spacecay.expenselog.ui.rememberBookData
import com.spacecay.expenselog.ui.theme.LocalMoney
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.util.PeriodKind
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(go: (String) -> Unit) {
    val bd = rememberBookData()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val hidden by Graph.prefs.hideAmounts.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AccountBalanceWallet, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Expense Log", style = MaterialTheme.typography.titleLarge)
                            Text(bd.book?.name ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    fun item(icon: ImageVector, label: String, route: String) = Triple(icon, label, route)
                    val groups = listOf(
                        listOf(
                            item(Icons.Filled.PieChart, "Budgets", "budgets"),
                            item(Icons.Filled.Flag, "Goals", "goals"),
                            item(Icons.Filled.Handshake, "Debts & loans", "debts"),
                            item(Icons.Filled.Repeat, "Recurring", "recurring"),
                            item(Icons.Filled.Bookmarks, "Templates", "templates"),
                        ),
                        listOf(
                            item(Icons.Filled.AutoAwesome, "Auto-log (telebirr / CBE)", "autolog"),
                            item(Icons.Filled.Today, "Today's log", "dailylog"),
                        ),
                        listOf(
                            item(Icons.Filled.AccountBalanceWallet, "Wallets", "wallets/manage"),
                            item(Icons.Filled.Category, "Categories", "categories"),
                            item(Icons.Filled.CurrencyExchange, "Currencies & rates", "currencies"),
                            item(Icons.AutoMirrored.Filled.MenuBook, "Books", "books"),
                        ),
                        listOf(
                            item(Icons.Filled.Backup, "Backup & export", "backup"),
                            item(Icons.Filled.Settings, "Settings", "settings"),
                        ),
                    )
                    groups.forEachIndexed { gi, g ->
                        g.forEach { (icon, label, route) ->
                            NavigationDrawerItem(
                                icon = { Icon(icon, null) }, label = { Text(label) }, selected = false,
                                onClick = { scope.launch { drawer.close() }; go(route) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                        if (gi < groups.size - 1) HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                var bookMenu by remember { mutableStateOf(false) }
                val books by remember { Graph.dao.observeBooks() }.collectAsStateWithLifecycle(emptyList())
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, "Menu") } },
                    title = {
                        Box {
                            Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { bookMenu = true }.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(listOf("Records", "Calendar", "Statistics", "Wallets")[tab])
                                if (books.size > 1) {
                                    Text("  · ${bd.book?.name ?: ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Icon(Icons.Filled.KeyboardArrowDown, null)
                                }
                            }
                            DropdownMenu(expanded = bookMenu && books.size > 1, onDismissRequest = { bookMenu = false }) {
                                books.forEach { b ->
                                    DropdownMenuItem(text = { Text(b.name + " (${b.mainCurrency})") }, onClick = { Graph.prefs.setBook(b.id); bookMenu = false })
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { go("search") }) { Icon(Icons.Filled.Search, "Search") }
                        IconButton(onClick = { Graph.prefs.setHideAmounts(!hidden) }) {
                            Icon(if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Hide amounts")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { go(if (tab == 3) "walletedit?id=0" else "txedit?id=0&type=0") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                ) { Icon(Icons.Filled.Add, "Add") }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                    val items = listOf(
                        Icons.AutoMirrored.Filled.ReceiptLong to "Records",
                        Icons.Filled.CalendarMonth to "Calendar",
                        Icons.Filled.BarChart to "Stats",
                        Icons.Filled.AccountBalanceWallet to "Wallets",
                    )
                    items.forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, label) }, label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> RecordsTab(bd, hidden, go)
                    1 -> CalendarTab(bd, hidden, go)
                    2 -> StatsTab(bd, hidden, go)
                    else -> WalletsTab(bd, hidden, go)
                }
            }
        }
    }
}

@Composable
fun HeroCard(bd: BookData, hidden: Boolean, income: Long, expense: Long, periodLabel: String) {
    val m = LocalMoney.current
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(m.heroStart, m.heroEnd))).padding(20.dp),
    ) {
        Column {
            Text("Total balance", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            Text(if (hidden) "••••••" else Money.format(bd.total, bd.main), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Row {
                HeroStat("Income", income, bd.main, hidden, Color(0xFFB9F5D6), Modifier.weight(1f))
                HeroStat("Expense", expense, bd.main, hidden, Color(0xFFFFC9CB), Modifier.weight(1f))
                HeroStat("Net", income - expense, bd.main, hidden, Color.White, Modifier.weight(1f))
            }
            Text(periodLabel, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun HeroStat(label: String, cents: Long, code: String, hidden: Boolean, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
        Text(if (hidden) "••••" else Money.compact(cents), color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RecordsTab(bd: BookData, hidden: Boolean, go: (String) -> Unit) {
    var anchor by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var kindIdx by rememberSaveable { mutableIntStateOf(PeriodKind.MONTH.ordinal) }
    val kind = PeriodKind.entries[kindIdx]
    val range = Dates.range(kind, anchor)
    val txns by remember(bd.bookId, range) { Graph.dao.observeTxnsBetween(bd.bookId, range.start, range.end) }.collectAsStateWithLifecycle(emptyList())
    val unreviewed by remember(bd.bookId) { Graph.dao.observeUnreviewed(bd.bookId) }.collectAsStateWithLifecycle(emptyList())
    var kindMenu by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Box {
                PeriodSwitcher(
                    Dates.label(kind, anchor),
                    onPrev = { anchor = Dates.shift(kind, anchor, -1) },
                    onNext = { anchor = Dates.shift(kind, anchor, 1) },
                    onLabelClick = { kindMenu = true },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                    PeriodKind.entries.forEach { k ->
                        DropdownMenuItem(text = { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = { kindIdx = k.ordinal; kindMenu = false })
                    }
                    DropdownMenuItem(text = { Text("Jump to today") }, onClick = { anchor = System.currentTimeMillis(); kindMenu = false })
                }
            }
        }
        item { HeroCard(bd, hidden, Stats.income(txns, bd), Stats.expense(txns, bd), "${txns.size} transactions · ${Dates.label(kind, anchor)}") }
        if (unreviewed.isNotEmpty()) item {
            AppCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), onClick = { go("review") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${unreviewed.size} auto-logged ${if (unreviewed.size == 1) "payment" else "payments"} to review", style = MaterialTheme.typography.titleSmall)
                        Text("Check the category, then confirm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        if (txns.isEmpty()) item {
            EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "No transactions", "Tap + to add one, or turn on auto-log for telebirr and CBE.")
        }
        dayGroupedTxns(txns, bd, hidden) { go("tx/${it.id}") }
    }
}

@Composable
fun SmallStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}
