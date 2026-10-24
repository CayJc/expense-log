package com.spacecay.expenselog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.data.Book
import com.spacecay.expenselog.data.Category
import com.spacecay.expenselog.data.CurrencyRate
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.Rates
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.data.Txn
import com.spacecay.expenselog.data.Wallet
import com.spacecay.expenselog.data.WalletDelta
import com.spacecay.expenselog.util.AppIcons

data class BookData(
    val bookId: Long,
    val book: Book?,
    val rateList: List<CurrencyRate>,
    val wallets: List<Wallet>,
    val categories: List<Category>,
    val deltas: List<WalletDelta>,
) {
    val rates: Rates = Rates.of(book, rateList)
    val main: String get() = book?.mainCurrency ?: "ETB"
    val walletMap: Map<Long, Wallet> = wallets.associateBy { it.id }
    val catMap: Map<Long, Category> = categories.associateBy { it.id }
    private val deltaMap = deltas.associate { it.walletId to it.delta }

    fun currencyOf(walletId: Long?): String = walletId?.let { walletMap[it]?.currency } ?: main
    fun balance(w: Wallet): Long = w.initialBalance + (deltaMap[w.id] ?: 0L)

    /** Net worth in main currency over non-excluded, non-archived wallets. */
    val total: Long
        get() = wallets.filter { !it.archived && !it.excludeFromTotal }.sumOf { rates.toMain(balance(it), it.currency) }

    /** Amount of a transaction expressed in the main currency. */
    fun mainAmount(t: Txn): Long = rates.toMain(t.amount, currencyOf(t.walletId))

    /** Parent category (or itself if top-level). */
    fun topCategory(id: Long?): Category? {
        val c = id?.let { catMap[it] } ?: return null
        return c.parentId?.let { catMap[it] } ?: c
    }

    fun categoryLabel(id: Long?): String {
        val c = id?.let { catMap[it] } ?: return "Uncategorized"
        val p = c.parentId?.let { catMap[it] }
        return if (p != null) "${p.name} · ${c.name}" else c.name
    }

    fun display(t: Txn): TxDisplay {
        if (t.type == TxType.TRANSFER) {
            val from = walletMap[t.walletId]?.name ?: "?"
            val to = walletMap[t.toWalletId ?: -1L]?.name ?: "?"
            return TxDisplay("Transfer", "$from → $to", Icons.Filled.SwapHoriz, Color(0xFF6B7AA1))
        }
        if (t.debtId != null) return TxDisplay(t.note.ifBlank { "Debt" }, walletMap[t.walletId]?.name ?: "", Icons.Filled.Handshake, Color(0xFFFF7043))
        if (t.goalId != null) return TxDisplay(t.note.ifBlank { "Goal" }, walletMap[t.walletId]?.name ?: "", Icons.Filled.Flag, Color(0xFF26A69A))
        val c = t.categoryId?.let { catMap[it] }
        val title = c?.name ?: "Uncategorized"
        val sub = listOfNotNull(
            t.note.takeIf { it.isNotBlank() } ?: t.payee.takeIf { it.isNotBlank() },
            walletMap[t.walletId]?.name,
        ).joinToString(" · ")
        return TxDisplay(title, sub, if (c != null) AppIcons.get(c.icon) else Icons.Filled.HelpOutline, if (c != null) Color(c.color) else Color(0xFF9AA0AE))
    }
}

data class TxDisplay(val title: String, val subtitle: String, val icon: ImageVector, val color: Color)

@Composable
fun rememberBookData(): BookData {
    val dao = Graph.dao
    val bookId by Graph.prefs.bookId.collectAsStateWithLifecycle()
    val book by remember(bookId) { dao.observeBook(bookId) }.collectAsStateWithLifecycle(null)
    val rates by remember(bookId) { dao.observeRates(bookId) }.collectAsStateWithLifecycle(emptyList())
    val wallets by remember(bookId) { dao.observeWallets(bookId) }.collectAsStateWithLifecycle(emptyList())
    val cats by remember(bookId) { dao.observeCategories(bookId) }.collectAsStateWithLifecycle(emptyList())
    val deltas by remember(bookId) { dao.observeWalletDeltas(bookId) }.collectAsStateWithLifecycle(emptyList())
    return remember(bookId, book, rates, wallets, cats, deltas) { BookData(bookId, book, rates, wallets, cats, deltas) }
}

/** Totals helpers. Transfers and debt/goal movements never count as income/expense. */
object Stats {
    fun counts(t: Txn) = t.type != TxType.TRANSFER && !t.excludeFromStats && t.debtId == null && t.goalId == null

    fun income(list: List<Txn>, bd: BookData) = list.filter { counts(it) && it.type == TxType.INCOME }.sumOf { bd.mainAmount(it) }
    fun expense(list: List<Txn>, bd: BookData) = list.filter { counts(it) && it.type == TxType.EXPENSE }.sumOf { bd.mainAmount(it) + bd.rates.toMain(it.fee, bd.currencyOf(it.walletId)) }

    data class Slice(val key: Long?, val name: String, val color: Color, val icon: ImageVector, val amount: Long, val count: Int)

    /** Group by top-level category. */
    fun byCategory(list: List<Txn>, bd: BookData, type: Int): List<Slice> {
        return list.filter { counts(it) && it.type == type }
            .groupBy { bd.topCategory(it.categoryId)?.id }
            .map { (id, txs) ->
                val c = id?.let { bd.catMap[it] }
                Slice(id, c?.name ?: "Uncategorized", c?.let { Color(it.color) } ?: Color(0xFF9AA0AE),
                    c?.let { AppIcons.get(it.icon) } ?: Icons.Filled.HelpOutline, txs.sumOf { bd.mainAmount(it) }, txs.size)
            }
            .sortedByDescending { it.amount }
    }

    /** Group by exact (sub)category within one parent. */
    fun bySubcategory(list: List<Txn>, bd: BookData, parentId: Long): List<Slice> {
        return list.filter { counts(it) && bd.topCategory(it.categoryId)?.id == parentId }
            .groupBy { it.categoryId }
            .map { (id, txs) ->
                val c = id?.let { bd.catMap[it] }
                Slice(id, if (id == parentId) "${c?.name ?: ""} (general)" else c?.name ?: "?", c?.let { Color(it.color) } ?: Color.Gray,
                    c?.let { AppIcons.get(it.icon) } ?: Icons.Filled.HelpOutline, txs.sumOf { bd.mainAmount(it) }, txs.size)
            }
            .sortedByDescending { it.amount }
    }
}
