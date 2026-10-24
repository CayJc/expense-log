package com.spacecay.expenselog.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // ---------- Books ----------
    @Query("SELECT * FROM books ORDER BY sortOrder, id")
    fun observeBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY sortOrder, id")
    suspend fun getBooks(): List<Book>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): Book?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): Flow<Book?>

    @Insert
    suspend fun insertBook(b: Book): Long

    @Update
    suspend fun updateBook(b: Book)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    // ---------- Currency rates ----------
    @Query("SELECT * FROM currency_rates WHERE bookId = :bookId ORDER BY code")
    fun observeRates(bookId: Long): Flow<List<CurrencyRate>>

    @Query("SELECT * FROM currency_rates WHERE bookId = :bookId")
    suspend fun getRates(bookId: Long): List<CurrencyRate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(r: CurrencyRate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRates(r: List<CurrencyRate>)

    @Query("DELETE FROM currency_rates WHERE bookId = :bookId AND code = :code")
    suspend fun deleteRate(bookId: Long, code: String)

    // ---------- Wallets ----------
    @Query("SELECT * FROM wallets WHERE bookId = :bookId ORDER BY sortOrder, id")
    fun observeWallets(bookId: Long): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets WHERE bookId = :bookId ORDER BY sortOrder, id")
    suspend fun getWallets(bookId: Long): List<Wallet>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getWallet(id: Long): Wallet?

    @Insert
    suspend fun insertWallet(w: Wallet): Long

    @Update
    suspend fun updateWallet(w: Wallet)

    @Update
    suspend fun updateWallets(w: List<Wallet>)

    @Query("DELETE FROM wallets WHERE id = :id")
    suspend fun deleteWallet(id: Long)

    @Query(
        """
        SELECT w.id AS walletId,
          COALESCE((SELECT SUM(CASE WHEN t.type = 1 THEN t.amount
                                    WHEN t.type = 0 THEN -(t.amount + t.fee)
                                    ELSE -(t.amount + t.fee) END)
                    FROM transactions t WHERE t.walletId = w.id), 0)
          + COALESCE((SELECT SUM(COALESCE(t2.toAmount, t2.amount))
                      FROM transactions t2 WHERE t2.type = 2 AND t2.toWalletId = w.id), 0) AS delta
        FROM wallets w WHERE w.bookId = :bookId
        """
    )
    fun observeWalletDeltas(bookId: Long): Flow<List<WalletDelta>>

    @Query(
        """
        SELECT w.id AS walletId,
          COALESCE((SELECT SUM(CASE WHEN t.type = 1 THEN t.amount
                                    WHEN t.type = 0 THEN -(t.amount + t.fee)
                                    ELSE -(t.amount + t.fee) END)
                    FROM transactions t WHERE t.walletId = w.id), 0)
          + COALESCE((SELECT SUM(COALESCE(t2.toAmount, t2.amount))
                      FROM transactions t2 WHERE t2.type = 2 AND t2.toWalletId = w.id), 0) AS delta
        FROM wallets w WHERE w.bookId = :bookId
        """
    )
    suspend fun getWalletDeltas(bookId: Long): List<WalletDelta>

    // ---------- Categories ----------
    @Query("SELECT * FROM categories WHERE bookId = :bookId ORDER BY sortOrder, id")
    fun observeCategories(bookId: Long): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE bookId = :bookId ORDER BY sortOrder, id")
    suspend fun getCategories(bookId: Long): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: Long): Category?

    @Insert
    suspend fun insertCategory(c: Category): Long

    @Insert
    suspend fun insertCategories(c: List<Category>): List<Long>

    @Update
    suspend fun updateCategory(c: Category)

    @Update
    suspend fun updateCategories(c: List<Category>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: Long, to: Long?)

    @Query("UPDATE categories SET parentId = NULL WHERE parentId = :parentId")
    suspend fun detachSubcategories(parentId: Long)

    // ---------- Transactions ----------
    @Query("SELECT * FROM transactions WHERE bookId = :bookId AND dateTime >= :from AND dateTime < :to ORDER BY dateTime DESC, id DESC")
    fun observeTxnsBetween(bookId: Long, from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE bookId = :bookId AND dateTime >= :from AND dateTime < :to ORDER BY dateTime DESC, id DESC")
    suspend fun getTxnsBetween(bookId: Long, from: Long, to: Long): List<Txn>

    @Query("SELECT * FROM transactions WHERE bookId = :bookId ORDER BY dateTime DESC, id DESC")
    fun observeAllTxns(bookId: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE bookId = :bookId ORDER BY dateTime DESC, id DESC")
    suspend fun getAllTxns(bookId: Long): List<Txn>

    @Query("SELECT * FROM transactions WHERE walletId = :walletId OR toWalletId = :walletId ORDER BY dateTime DESC, id DESC")
    fun observeWalletTxns(walletId: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeTxn(id: Long): Flow<Txn?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTxn(id: Long): Txn?

    @Insert
    suspend fun insertTxn(t: Txn): Long

    @Update
    suspend fun updateTxn(t: Txn)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTxn(id: Long)

    @Query("DELETE FROM transactions WHERE walletId = :walletId OR toWalletId = :walletId")
    suspend fun deleteTxnsOfWallet(walletId: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE bookId = :bookId AND ref = :ref")
    suspend fun countByRef(bookId: Long, ref: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE bookId = :bookId AND amount = :amount AND type = :type AND dateTime BETWEEN :from AND :to")
    suspend fun countSimilar(bookId: Long, amount: Long, type: Int, from: Long, to: Long): Int

    /** An auto-logged payment without a reference that matches (used to merge app + SMS copies of one payment). */
    @Query("SELECT * FROM transactions WHERE bookId = :bookId AND amount = :amount AND type = :type AND dateTime BETWEEN :from AND :to ORDER BY ABS(dateTime - :at) LIMIT 1")
    suspend fun findSimilar(bookId: Long, amount: Long, type: Int, from: Long, to: Long, at: Long): Txn?

    @Query("SELECT categoryId FROM transactions WHERE bookId = :bookId AND payee != '' AND LOWER(payee) = LOWER(:payee) AND categoryId IS NOT NULL AND reviewed = 1 GROUP BY categoryId ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun topCategoryForPayee(bookId: Long, payee: String): Long?

    @Query("SELECT * FROM transactions WHERE bookId = :bookId AND type = :type AND categoryId IS NOT NULL AND reviewed = 1 AND amount BETWEEN :lo AND :hi AND dateTime > :since")
    suspend fun similarAmounts(bookId: Long, type: Int, lo: Long, hi: Long, since: Long): List<Txn>

    @Query("SELECT * FROM transactions WHERE bookId = :bookId ORDER BY dateTime DESC, id DESC LIMIT :n")
    suspend fun latestTxns(bookId: Long, n: Int): List<Txn>

    @Query("SELECT * FROM transactions WHERE splitId = :splitId ORDER BY id")
    fun observeSplit(splitId: Long): Flow<List<Txn>>

    @Query("SELECT * FROM recurring WHERE active = 1 AND confirm = 1 AND nextDate <= :now")
    fun observeDueBills(now: Long): Flow<List<Recurring>>

    @Query("SELECT * FROM transactions WHERE bookId = :bookId AND reviewed = 0 ORDER BY dateTime DESC")
    fun observeUnreviewed(bookId: Long): Flow<List<Txn>>

    @Query("UPDATE transactions SET reviewed = 1 WHERE bookId = :bookId")
    suspend fun markAllReviewed(bookId: Long)

    @Query("SELECT * FROM transactions WHERE debtId = :debtId ORDER BY dateTime DESC")
    fun observeDebtTxns(debtId: Long): Flow<List<Txn>>

    @Query("SELECT MIN(dateTime) FROM transactions WHERE bookId = :bookId")
    suspend fun firstTxnDate(bookId: Long): Long?

    @Query("SELECT COUNT(*) FROM transactions WHERE walletId = :walletId OR toWalletId = :walletId")
    suspend fun countWalletTxns(walletId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :catId")
    suspend fun countCategoryTxns(catId: Long): Int

    // ---------- Budgets ----------
    @Query("SELECT * FROM budgets WHERE bookId = :bookId ORDER BY id")
    fun observeBudgets(bookId: Long): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getBudget(id: Long): Budget?

    @Insert
    suspend fun insertBudget(b: Budget): Long

    @Update
    suspend fun updateBudget(b: Budget)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudget(id: Long)

    // ---------- Goals ----------
    @Query("SELECT * FROM goals WHERE bookId = :bookId ORDER BY reached, id")
    fun observeGoals(bookId: Long): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoal(id: Long): Goal?

    @Insert
    suspend fun insertGoal(g: Goal): Long

    @Update
    suspend fun updateGoal(g: Goal)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("SELECT * FROM goal_entries ORDER BY date DESC, id DESC")
    fun observeAllGoalEntries(): Flow<List<GoalEntry>>

    @Query("SELECT * FROM goal_entries WHERE goalId = :goalId ORDER BY date DESC, id DESC")
    fun observeGoalEntries(goalId: Long): Flow<List<GoalEntry>>

    @Query("SELECT * FROM goal_entries WHERE goalId = :goalId")
    suspend fun getGoalEntries(goalId: Long): List<GoalEntry>

    @Insert
    suspend fun insertGoalEntry(e: GoalEntry): Long

    @Delete
    suspend fun deleteGoalEntry(e: GoalEntry)

    @Query("DELETE FROM goal_entries WHERE goalId = :goalId")
    suspend fun deleteGoalEntries(goalId: Long)

    // ---------- Debts ----------
    @Query("SELECT * FROM debts WHERE bookId = :bookId ORDER BY closed, id DESC")
    fun observeDebts(bookId: Long): Flow<List<Debt>>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebt(id: Long): Debt?

    @Insert
    suspend fun insertDebt(d: Debt): Long

    @Update
    suspend fun updateDebt(d: Debt)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebt(id: Long)

    @Query("SELECT * FROM debt_entries ORDER BY date DESC, id DESC")
    fun observeAllDebtEntries(): Flow<List<DebtEntry>>

    @Query("SELECT * FROM debt_entries WHERE debtId = :debtId ORDER BY date DESC, id DESC")
    fun observeDebtEntries(debtId: Long): Flow<List<DebtEntry>>

    @Query("SELECT * FROM debt_entries WHERE debtId = :debtId")
    suspend fun getDebtEntries(debtId: Long): List<DebtEntry>

    @Insert
    suspend fun insertDebtEntry(e: DebtEntry): Long

    @Delete
    suspend fun deleteDebtEntry(e: DebtEntry)

    @Query("DELETE FROM debt_entries WHERE debtId = :debtId")
    suspend fun deleteDebtEntries(debtId: Long)

    // ---------- Recurring ----------
    @Query("SELECT * FROM recurring WHERE bookId = :bookId ORDER BY active DESC, nextDate")
    fun observeRecurring(bookId: Long): Flow<List<Recurring>>

    @Query("SELECT * FROM recurring WHERE active = 1 AND confirm = 0 AND nextDate <= :now")
    suspend fun getDueRecurring(now: Long): List<Recurring>

    @Query("SELECT * FROM recurring WHERE active = 1 AND confirm = 1 AND nextDate <= :now")
    suspend fun getDueBills(now: Long): List<Recurring>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun getRecurring(id: Long): Recurring?

    @Insert
    suspend fun insertRecurring(r: Recurring): Long

    @Update
    suspend fun updateRecurring(r: Recurring)

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun deleteRecurring(id: Long)

    // ---------- Templates ----------
    @Query("SELECT * FROM templates WHERE bookId = :bookId ORDER BY sortOrder, id")
    fun observeTemplates(bookId: Long): Flow<List<Template>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplate(id: Long): Template?

    @Insert
    suspend fun insertTemplate(t: Template): Long

    @Update
    suspend fun updateTemplate(t: Template)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    // ---------- Payee rules ----------
    @Query("SELECT * FROM payee_rules WHERE bookId = :bookId ORDER BY keyword")
    fun observeRules(bookId: Long): Flow<List<PayeeRule>>

    @Query("SELECT * FROM payee_rules WHERE bookId = :bookId")
    suspend fun getRules(bookId: Long): List<PayeeRule>

    @Insert
    suspend fun insertRule(r: PayeeRule): Long

    @Query("DELETE FROM payee_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    // ---------- Backup (all rows) ----------
    @Query("SELECT * FROM books") suspend fun allBooks(): List<Book>
    @Query("SELECT * FROM currency_rates") suspend fun allRates(): List<CurrencyRate>
    @Query("SELECT * FROM wallets") suspend fun allWallets(): List<Wallet>
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM transactions") suspend fun allTxns(): List<Txn>
    @Query("SELECT * FROM budgets") suspend fun allBudgets(): List<Budget>
    @Query("SELECT * FROM goals") suspend fun allGoals(): List<Goal>
    @Query("SELECT * FROM goal_entries") suspend fun allGoalEntries(): List<GoalEntry>
    @Query("SELECT * FROM debts") suspend fun allDebts(): List<Debt>
    @Query("SELECT * FROM debt_entries") suspend fun allDebtEntries(): List<DebtEntry>
    @Query("SELECT * FROM recurring") suspend fun allRecurring(): List<Recurring>
    @Query("SELECT * FROM templates") suspend fun allTemplates(): List<Template>
    @Query("SELECT * FROM payee_rules") suspend fun allRules(): List<PayeeRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBooks(x: List<Book>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWallets(x: List<Wallet>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategories(x: List<Category>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTxns(x: List<Txn>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudgets(x: List<Budget>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGoals(x: List<Goal>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGoalEntries(x: List<GoalEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDebts(x: List<Debt>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDebtEntries(x: List<DebtEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecurring(x: List<Recurring>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTemplates(x: List<Template>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRules(x: List<PayeeRule>)

    @Query("DELETE FROM books") suspend fun wipeBooks()
    @Query("DELETE FROM currency_rates") suspend fun wipeRates()
    @Query("DELETE FROM wallets") suspend fun wipeWallets()
    @Query("DELETE FROM categories") suspend fun wipeCategories()
    @Query("DELETE FROM transactions") suspend fun wipeTxns()
    @Query("DELETE FROM budgets") suspend fun wipeBudgets()
    @Query("DELETE FROM goals") suspend fun wipeGoals()
    @Query("DELETE FROM goal_entries") suspend fun wipeGoalEntries()
    @Query("DELETE FROM debts") suspend fun wipeDebts()
    @Query("DELETE FROM debt_entries") suspend fun wipeDebtEntries()
    @Query("DELETE FROM recurring") suspend fun wipeRecurring()
    @Query("DELETE FROM templates") suspend fun wipeTemplates()
    @Query("DELETE FROM payee_rules") suspend fun wipeRules()

    // Book cascade
    @Query("DELETE FROM currency_rates WHERE bookId = :b") suspend fun delRatesOf(b: Long)
    @Query("DELETE FROM wallets WHERE bookId = :b") suspend fun delWalletsOf(b: Long)
    @Query("DELETE FROM categories WHERE bookId = :b") suspend fun delCategoriesOf(b: Long)
    @Query("DELETE FROM transactions WHERE bookId = :b") suspend fun delTxnsOf(b: Long)
    @Query("DELETE FROM budgets WHERE bookId = :b") suspend fun delBudgetsOf(b: Long)
    @Query("DELETE FROM goal_entries WHERE goalId IN (SELECT id FROM goals WHERE bookId = :b)") suspend fun delGoalEntriesOf(b: Long)
    @Query("DELETE FROM goals WHERE bookId = :b") suspend fun delGoalsOf(b: Long)
    @Query("DELETE FROM debt_entries WHERE debtId IN (SELECT id FROM debts WHERE bookId = :b)") suspend fun delDebtEntriesOf(b: Long)
    @Query("DELETE FROM debts WHERE bookId = :b") suspend fun delDebtsOf(b: Long)
    @Query("DELETE FROM recurring WHERE bookId = :b") suspend fun delRecurringOf(b: Long)
    @Query("DELETE FROM templates WHERE bookId = :b") suspend fun delTemplatesOf(b: Long)
    @Query("DELETE FROM payee_rules WHERE bookId = :b") suspend fun delRulesOf(b: Long)
}
