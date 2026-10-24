package com.spacecay.expenselog.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A separate ledger ("book"), each with its own main currency. */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mainCurrency: String,
    val sortOrder: Int = 0,
)

/**
 * Exchange rate for a currency inside a book.
 * rate = how many units of the book's main currency equal 1 unit of [code].
 * The main currency itself is stored with rate 1.0. Always editable.
 */
@Entity(tableName = "currency_rates", primaryKeys = ["bookId", "code"])
data class CurrencyRate(
    val bookId: Long,
    val code: String,
    val rate: Double,
    val updatedAt: Long = System.currentTimeMillis(),
)

object WalletType {
    const val CASH = 0
    const val BANK = 1
    const val MOBILE_MONEY = 2
    const val CARD = 3
    const val CREDIT = 4
    const val SAVINGS = 5
    const val INVESTMENT = 6
    const val OTHER = 7
    val labels = listOf("Cash", "Bank account", "Mobile money", "Debit card", "Credit card", "Savings", "Investment", "Other")
}

@Entity(tableName = "wallets", indices = [Index("bookId")])
data class Wallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val name: String,
    val type: Int = WalletType.CASH,
    val currency: String,
    val initialBalance: Long = 0,
    val color: Int,
    val icon: String,
    val archived: Boolean = false,
    val excludeFromTotal: Boolean = false,
    val creditLimit: Long = 0,
    val sortOrder: Int = 0,
)

object CatType {
    const val EXPENSE = 0
    const val INCOME = 1
}

@Entity(tableName = "categories", indices = [Index("bookId")])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val name: String,
    val type: Int,
    val color: Int,
    val icon: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

object TxType {
    const val EXPENSE = 0
    const val INCOME = 1
    const val TRANSFER = 2
}

object TxSource {
    const val MANUAL = 0
    const val NOTIFICATION = 1
    const val SCREENSHOT = 2
    const val RECURRING = 3
    const val SHARED_TEXT = 4
    const val DEBT = 5
    const val GOAL = 6
}

@Entity(
    tableName = "transactions",
    indices = [Index("bookId"), Index("walletId"), Index("toWalletId"), Index("dateTime"), Index("categoryId"), Index("ref")]
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val type: Int,
    /** Amount in the source wallet's currency, minor units (cents). Always positive. */
    val amount: Long,
    val walletId: Long,
    val toWalletId: Long? = null,
    /** For transfers between currencies: amount received in the destination wallet currency. */
    val toAmount: Long? = null,
    val fee: Long = 0,
    val categoryId: Long? = null,
    val note: String = "",
    val payee: String = "",
    val dateTime: Long,
    /** Photo file names separated by '|' (stored in app files/photos). */
    val photos: String = "",
    val source: Int = TxSource.MANUAL,
    val debtId: Long? = null,
    val goalId: Long? = null,
    val recurringId: Long? = null,
    /** Bank / telebirr transaction number, used to avoid logging the same payment twice. */
    val ref: String? = null,
    val reviewed: Boolean = true,
    val excludeFromStats: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

object BudgetPeriod {
    const val WEEKLY = 0
    const val MONTHLY = 1
    const val YEARLY = 2
    const val ONCE = 3
    val labels = listOf("Weekly", "Monthly", "Yearly", "One-time")
}

@Entity(tableName = "budgets", indices = [Index("bookId")])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val name: String,
    /** In the book's main currency. */
    val amount: Long,
    val period: Int = BudgetPeriod.MONTHLY,
    val startDate: Long,
    val endDate: Long? = null,
    /** Comma separated category ids; empty = all expense categories. */
    val categoryIds: String = "",
    /** Comma separated wallet ids; empty = all wallets. */
    val walletIds: String = "",
    val color: Int,
    val alertPercent: Int = 80,
)

@Entity(tableName = "goals", indices = [Index("bookId")])
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val name: String,
    val target: Long,
    val currency: String,
    val deadline: Long? = null,
    val color: Int,
    val icon: String,
    val reached: Boolean = false,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "goal_entries", indices = [Index("goalId")])
data class GoalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    /** Positive = deposit, negative = withdrawal. Goal currency. */
    val amount: Long,
    val date: Long,
    val note: String = "",
    val walletId: Long? = null,
    val txnId: Long? = null,
)

object DebtType {
    /** I borrowed money — I owe. */
    const val PAYABLE = 0
    /** I lent money — they owe me. */
    const val RECEIVABLE = 1
}

@Entity(tableName = "debts", indices = [Index("bookId")])
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val type: Int,
    val person: String,
    val amount: Long,
    val currency: String,
    val startDate: Long,
    val dueDate: Long? = null,
    val note: String = "",
    val color: Int,
    val closed: Boolean = false,
    val walletId: Long? = null,
    val txnId: Long? = null,
)

@Entity(tableName = "debt_entries", indices = [Index("debtId")])
data class DebtEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    /** Positive = repayment, negative = additional borrowing/lending. */
    val amount: Long,
    val date: Long,
    val note: String = "",
    val walletId: Long? = null,
    val txnId: Long? = null,
)

object Freq {
    const val DAILY = 0
    const val WEEKLY = 1
    const val MONTHLY = 2
    const val YEARLY = 3
    val labels = listOf("Daily", "Weekly", "Monthly", "Yearly")
    val units = listOf("day", "week", "month", "year")
}

@Entity(tableName = "recurring", indices = [Index("bookId")])
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val type: Int,
    val amount: Long,
    val walletId: Long,
    val toWalletId: Long? = null,
    val toAmount: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val frequency: Int = Freq.MONTHLY,
    val interval: Int = 1,
    val startDate: Long,
    val nextDate: Long,
    val endDate: Long? = null,
    val active: Boolean = true,
)

@Entity(tableName = "templates", indices = [Index("bookId")])
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val name: String,
    val type: Int,
    val amount: Long,
    val walletId: Long? = null,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val sortOrder: Int = 0,
)

/** Auto-categorisation: if a logged payment's payee/text contains [keyword], use [categoryId]. */
@Entity(tableName = "payee_rules", indices = [Index("bookId")])
data class PayeeRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val keyword: String,
    val categoryId: Long,
)

/** Wallet balance change computed in SQL. */
data class WalletDelta(val walletId: Long, val delta: Long)
