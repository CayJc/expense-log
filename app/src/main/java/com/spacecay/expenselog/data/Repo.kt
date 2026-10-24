package com.spacecay.expenselog.data

import android.content.Context
import androidx.room.withTransaction
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.widget.SummaryWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/** Converts amounts into the book's main currency using the current (editable) rates. */
class Rates(val main: String, private val rates: Map<String, Double>) {
    fun rate(code: String): Double = if (code == main) 1.0 else rates[code] ?: 1.0
    fun toMain(cents: Long, code: String): Long = if (code == main) cents else Money.convert(cents, rate(code))
    fun convert(cents: Long, from: String, to: String): Long {
        if (from == to) return cents
        val inMain = cents * rate(from)
        return Math.round(inMain / rate(to))
    }
    fun has(code: String) = code == main || rates.containsKey(code)
    companion object {
        fun of(book: Book?, list: List<CurrencyRate>) =
            Rates(book?.mainCurrency ?: "ETB", list.associate { it.code to it.rate })
    }
}

object Graph {
    lateinit var db: AppDatabase
    lateinit var dao: AppDao
    lateinit var prefs: Prefs
    lateinit var repo: Repo
    lateinit var app: Context
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (::db.isInitialized) return
        app = context.applicationContext
        db = AppDatabase.get(app)
        dao = db.dao()
        prefs = Prefs(app)
        repo = Repo(app, db, dao, prefs)
        Dates.firstDayOfWeek = prefs.firstDayOfWeek
        Dates.monthStartDay = prefs.monthStartDay
    }
}

class Repo(private val ctx: Context, private val db: AppDatabase, val dao: AppDao, private val prefs: Prefs) {

    private val seedLock = kotlinx.coroutines.sync.Mutex()

    suspend fun ensureSeeded() = seedLock.withLock { seedInternal() }

    private suspend fun seedInternal() {
        if (dao.getBooks().isEmpty()) {
            val id = dao.insertBook(Book(name = "Personal", mainCurrency = "ETB"))
            Seed.populateBook(dao, id, "ETB", withWallets = true)
            prefs.setBook(id)
            val wallets = dao.getWallets(id)
            wallets.firstOrNull { it.type == WalletType.MOBILE_MONEY }?.let { prefs.telebirrWalletId = it.id }
            wallets.firstOrNull { it.type == WalletType.BANK }?.let { prefs.cbeWalletId = it.id }
        }
        val books = dao.getBooks()
        if (books.none { it.id == prefs.bookId.value }) prefs.setBook(books.first().id)
        prefs.seeded = true
        importV2Log()
    }

    /**
     * Expense Log 2.x kept every payment as a tab-separated line in files/transactions.tsv:
     * time, source, title, amount, IN|OUT, raw, txnId. Bring those in once.
     */
    private suspend fun importV2Log() {
        val f = java.io.File(ctx.filesDir, "transactions.tsv")
        if (!f.exists()) return
        val bookId = prefs.bookId.value
        val wallets = dao.getWallets(bookId)
        fun walletFor(source: String): Long? = when {
            source.contains("tele", true) -> wallets.firstOrNull { it.id == prefs.telebirrWalletId } ?: wallets.firstOrNull { it.type == WalletType.MOBILE_MONEY }
            source.contains("cbe", true) -> wallets.firstOrNull { it.id == prefs.cbeWalletId } ?: wallets.firstOrNull { it.type == WalletType.BANK }
            else -> wallets.firstOrNull { it.type == WalletType.CASH }
        }?.id ?: wallets.firstOrNull()?.id
        val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
        var count = 0
        db.withTransaction {
            f.readLines().forEach { line ->
                val p = line.split('\t')
                if (p.size < 5) return@forEach
                val time = p[0].toLongOrNull() ?: return@forEach
                val amount = p[3].toDoubleOrNull()?.let { Math.round(it * 100) } ?: return@forEach
                if (amount <= 0) return@forEach
                val type = if (p[4] == "IN") TxType.INCOME else TxType.EXPENSE
                val ref = p.getOrNull(6)?.trim()?.takeIf { it.isNotEmpty() }
                if (ref != null && dao.countByRef(bookId, ref) > 0) return@forEach
                val wallet = walletFor(p[1]) ?: return@forEach
                val title = p[2].trim()
                dao.insertTxn(
                    Txn(
                        bookId = bookId, type = type, amount = amount, walletId = wallet,
                        categoryId = guessCategory(bookId, title + " " + p.getOrNull(5).orEmpty(), type),
                        note = title.take(80), dateTime = time, source = TxSource.NOTIFICATION, ref = ref,
                        reviewed = time < weekAgo,
                    )
                )
                count++
            }
        }
        f.renameTo(java.io.File(ctx.filesDir, "transactions.v2-imported.tsv"))
        if (count > 0) refreshWidget()
    }

    val bookId get() = prefs.bookId.value

    suspend fun rates(bookId: Long = this.bookId): Rates = Rates.of(dao.getBook(bookId), dao.getRates(bookId))

    fun refreshWidget() { SummaryWidget.requestUpdate(ctx); com.spacecay.expenselog.widget.RecentWidget.requestUpdate(ctx) }

    // ---------------- Books ----------------
    suspend fun createBook(name: String, currency: String, withWallets: Boolean): Long = db.withTransaction {
        val id = dao.insertBook(Book(name = name, mainCurrency = currency, sortOrder = dao.getBooks().size))
        Seed.populateBook(dao, id, currency, withWallets)
        id
    }

    suspend fun deleteBook(id: Long) = db.withTransaction {
        dao.delRatesOf(id); dao.delTxnsOf(id); dao.delWalletsOf(id); dao.delCategoriesOf(id)
        dao.delBudgetsOf(id); dao.delGoalEntriesOf(id); dao.delGoalsOf(id); dao.delDebtEntriesOf(id)
        dao.delDebtsOf(id); dao.delRecurringOf(id); dao.delTemplatesOf(id); dao.delRulesOf(id)
        dao.deleteBook(id)
    }

    // ---------------- Currencies ----------------
    /**
     * Change a book's main currency at any time. All stored rates are re-based so that
     * conversions stay the same: newRate(x) = oldRate(x) / oldRate(newMain).
     */
    suspend fun changeMainCurrency(bookId: Long, newMain: String, rateOfNewMain: Double? = null) = db.withTransaction {
        val book = dao.getBook(bookId) ?: return@withTransaction
        if (book.mainCurrency == newMain) return@withTransaction
        val list = dao.getRates(bookId).associate { it.code to it.rate }.toMutableMap()
        list[book.mainCurrency] = 1.0
        val pivot = rateOfNewMain ?: list[newMain] ?: 1.0
        list[newMain] = pivot
        val now = System.currentTimeMillis()
        val rebased = list.map { (code, r) -> CurrencyRate(bookId, code, if (code == newMain) 1.0 else r / pivot, now) }
        dao.upsertRates(rebased)
        dao.updateBook(book.copy(mainCurrency = newMain))
    }

    suspend fun setRate(bookId: Long, code: String, rate: Double) {
        dao.upsertRate(CurrencyRate(bookId, code, rate, System.currentTimeMillis()))
        refreshWidget()
    }

    // ---------------- Transactions ----------------
    suspend fun saveTxn(t: Txn): Long {
        val id = if (t.id == 0L) dao.insertTxn(t) else { dao.updateTxn(t); t.id }
        refreshWidget()
        return id
    }

    suspend fun deleteTxn(id: Long) {
        val t = dao.getTxn(id) ?: return
        db.withTransaction {
            dao.deleteTxn(id)
            // keep linked debt / goal entries consistent
            t.debtId?.let { did -> dao.getDebtEntries(did).filter { it.txnId == id }.forEach { dao.deleteDebtEntry(it) } }
            t.goalId?.let { gid -> dao.getGoalEntries(gid).filter { it.txnId == id }.forEach { dao.deleteGoalEntry(it) } }
        }
        refreshWidget()
    }

    // ---------------- Debts ----------------
    suspend fun createDebt(d: Debt): Long = db.withTransaction {
        val id = dao.insertDebt(d)
        if (d.walletId != null) {
            // Borrowing brings money in; lending sends money out.
            val txId = dao.insertTxn(
                Txn(
                    bookId = d.bookId, type = if (d.type == DebtType.PAYABLE) TxType.INCOME else TxType.EXPENSE,
                    amount = d.amount, walletId = d.walletId, note = (if (d.type == DebtType.PAYABLE) "Borrowed from " else "Lent to ") + d.person,
                    payee = d.person, dateTime = d.startDate, source = TxSource.DEBT, debtId = id, excludeFromStats = true,
                )
            )
            dao.updateDebt(d.copy(id = id, txnId = txId))
        }
        id
    }.also { refreshWidget() }

    /** amount > 0: repayment; amount < 0: increase the debt. */
    suspend fun addDebtEntry(debt: Debt, amount: Long, date: Long, note: String, walletId: Long?) = db.withTransaction {
        var txId: Long? = null
        if (walletId != null && amount != 0L) {
            val repay = amount > 0
            // Paying back what I owe = money out. Receiving repayment = money in.
            val type = when (debt.type) {
                DebtType.PAYABLE -> if (repay) TxType.EXPENSE else TxType.INCOME
                else -> if (repay) TxType.INCOME else TxType.EXPENSE
            }
            val label = when (debt.type) {
                DebtType.PAYABLE -> if (repay) "Repaid ${debt.person}" else "Borrowed more from ${debt.person}"
                else -> if (repay) "${debt.person} repaid" else "Lent more to ${debt.person}"
            }
            txId = dao.insertTxn(
                Txn(bookId = debt.bookId, type = type, amount = kotlin.math.abs(amount), walletId = walletId, note = if (note.isBlank()) label else note,
                    payee = debt.person, dateTime = date, source = TxSource.DEBT, debtId = debt.id, excludeFromStats = true)
            )
        }
        dao.insertDebtEntry(DebtEntry(debtId = debt.id, amount = amount, date = date, note = note, walletId = walletId, txnId = txId))
        val entries = dao.getDebtEntries(debt.id)
        val paid = entries.filter { it.amount > 0 }.sumOf { it.amount }
        val extra = entries.filter { it.amount < 0 }.sumOf { -it.amount }
        dao.updateDebt(debt.copy(closed = paid >= debt.amount + extra))
    }.also { refreshWidget() }

    suspend fun deleteDebtEntry(e: DebtEntry) = db.withTransaction {
        e.txnId?.let { dao.deleteTxn(it) }
        dao.deleteDebtEntry(e)
    }.also { refreshWidget() }

    suspend fun deleteDebt(d: Debt, removeTxns: Boolean) = db.withTransaction {
        if (removeTxns) {
            d.txnId?.let { dao.deleteTxn(it) }
            dao.getDebtEntries(d.id).forEach { e -> e.txnId?.let { dao.deleteTxn(it) } }
        }
        dao.deleteDebtEntries(d.id)
        dao.deleteDebt(d.id)
    }.also { refreshWidget() }

    // ---------------- Goals ----------------
    suspend fun addGoalEntry(g: Goal, amount: Long, date: Long, note: String, walletId: Long?) = db.withTransaction {
        var txId: Long? = null
        if (walletId != null && amount != 0L) {
            val w = dao.getWallet(walletId)
            val r = rates(g.bookId)
            val walletAmount = if (w != null) r.convert(kotlin.math.abs(amount), g.currency, w.currency) else kotlin.math.abs(amount)
            txId = dao.insertTxn(
                Txn(bookId = g.bookId, type = if (amount > 0) TxType.EXPENSE else TxType.INCOME, amount = walletAmount, walletId = walletId,
                    note = if (note.isBlank()) (if (amount > 0) "Saved for ${g.name}" else "Withdrew from ${g.name}") else note,
                    dateTime = date, source = TxSource.GOAL, goalId = g.id, excludeFromStats = true)
            )
        }
        dao.insertGoalEntry(GoalEntry(goalId = g.id, amount = amount, date = date, note = note, walletId = walletId, txnId = txId))
        val saved = dao.getGoalEntries(g.id).sumOf { it.amount }
        dao.updateGoal(g.copy(reached = saved >= g.target))
    }.also { refreshWidget() }

    suspend fun deleteGoalEntry(e: GoalEntry) = db.withTransaction {
        e.txnId?.let { dao.deleteTxn(it) }
        dao.deleteGoalEntry(e)
    }.also { refreshWidget() }

    suspend fun deleteGoal(g: Goal, removeTxns: Boolean) = db.withTransaction {
        if (removeTxns) dao.getGoalEntries(g.id).forEach { e -> e.txnId?.let { dao.deleteTxn(it) } }
        dao.deleteGoalEntries(g.id)
        dao.deleteGoal(g.id)
    }.also { refreshWidget() }

    // ---------------- Wallets / categories ----------------
    suspend fun deleteWallet(id: Long) = db.withTransaction {
        dao.deleteTxnsOfWallet(id)
        dao.deleteWallet(id)
    }.also { refreshWidget() }

    suspend fun deleteCategory(c: Category, moveTo: Long?) = db.withTransaction {
        dao.reassignCategory(c.id, moveTo)
        if (c.parentId == null) {
            // subcategories go with the parent
            dao.getCategories(c.bookId).filter { it.parentId == c.id }.forEach {
                dao.reassignCategory(it.id, moveTo)
                dao.deleteCategory(it.id)
            }
        }
        dao.deleteCategory(c.id)
    }

    // ---------------- Recurring ----------------
    fun nextOccurrence(r: Recurring, from: Long): Long {
        val c = Dates.cal(from)
        when (r.frequency) {
            Freq.DAILY -> c.add(Calendar.DAY_OF_MONTH, r.interval)
            Freq.WEEKLY -> c.add(Calendar.WEEK_OF_YEAR, r.interval)
            Freq.MONTHLY -> {
                val dom = Dates.cal(r.startDate).get(Calendar.DAY_OF_MONTH)
                c.set(Calendar.DAY_OF_MONTH, 1)
                c.add(Calendar.MONTH, r.interval)
                c.set(Calendar.DAY_OF_MONTH, minOf(dom, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
            else -> c.add(Calendar.YEAR, r.interval)
        }
        return c.timeInMillis
    }

    /** Creates every transaction that has come due. Safe to call often. */
    suspend fun runDueRecurring(): Int {
        val now = System.currentTimeMillis()
        var created = 0
        for (r0 in dao.getDueRecurring(now)) {
            var r = r0
            var guard = 0
            while (r.active && r.nextDate <= now && guard++ < 400) {
                if (r.endDate != null && r.nextDate > r.endDate!!) { r = r.copy(active = false); break }
                dao.insertTxn(
                    Txn(bookId = r.bookId, type = r.type, amount = r.amount, walletId = r.walletId, toWalletId = r.toWalletId,
                        toAmount = r.toAmount, categoryId = r.categoryId, note = r.note, dateTime = r.nextDate,
                        source = TxSource.RECURRING, recurringId = r.id)
                )
                created++
                r = r.copy(nextDate = nextOccurrence(r, r.nextDate))
            }
            if (r.endDate != null && r.nextDate > r.endDate!!) r = r.copy(active = false)
            dao.updateRecurring(r)
        }
        if (created > 0) refreshWidget()
        return created
    }

    // ---------------- Bill reminders (recurring with confirm = true) ----------------
    suspend fun payBill(r: Recurring) {
        dao.insertTxn(
            Txn(bookId = r.bookId, type = r.type, amount = r.amount, walletId = r.walletId, toWalletId = r.toWalletId,
                toAmount = r.toAmount, categoryId = r.categoryId, note = r.note, dateTime = r.nextDate,
                source = TxSource.RECURRING, recurringId = r.id)
        )
        advanceBill(r)
        refreshWidget()
    }

    suspend fun advanceBill(r: Recurring) {
        val next = nextOccurrence(r, r.nextDate)
        val ended = r.endDate != null && next > r.endDate
        dao.updateRecurring(r.copy(nextDate = next, active = r.active && !ended))
    }

    suspend fun billTitle(r: Recurring): String =
        r.note.ifBlank { r.categoryId?.let { dao.getCategory(it)?.name } ?: "Bill" }

    suspend fun notifyDueBills(ctx: Context) {
        val sp = ctx.getSharedPreferences("bills", Context.MODE_PRIVATE)
        for (r in dao.getDueBills(System.currentTimeMillis())) {
            val key = "n${r.id}"
            if (sp.getLong(key, 0L) == r.nextDate) continue
            sp.edit().putLong(key, r.nextDate).apply()
            val cur = dao.getWallet(r.walletId)?.currency ?: "ETB"
            com.spacecay.expenselog.work.Notifier.billDue(ctx, r, billTitle(r), Money.format(r.amount, cur))
        }
    }

    // ---------------- Auto-categorisation ----------------
    suspend fun guessCategory(bookId: Long, text: String, type: Int, rulesOnly: Boolean = false): Long? {
        val lower = text.lowercase()
        dao.getRules(bookId).firstOrNull { it.keyword.isNotBlank() && lower.contains(it.keyword.lowercase()) }?.let { return it.categoryId }
        if (rulesOnly) return null
        val cats = dao.getCategories(bookId).filter { it.type == (if (type == TxType.INCOME) CatType.INCOME else CatType.EXPENSE) }
        for ((k, name) in Seed.payeeHints) {
            if (lower.contains(k)) cats.firstOrNull { it.name.equals(name, true) }?.let { return it.id }
        }
        return null
    }

    fun launch(block: suspend Repo.() -> Unit) = Graph.scope.launch { block() }
}
