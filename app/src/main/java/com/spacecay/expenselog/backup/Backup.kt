package com.spacecay.expenselog.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.spacecay.expenselog.data.Book
import com.spacecay.expenselog.data.Budget
import com.spacecay.expenselog.data.Category
import com.spacecay.expenselog.data.CurrencyRate
import com.spacecay.expenselog.data.Debt
import com.spacecay.expenselog.data.DebtEntry
import com.spacecay.expenselog.data.Goal
import com.spacecay.expenselog.data.GoalEntry
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.PayeeRule
import com.spacecay.expenselog.data.Recurring
import com.spacecay.expenselog.data.Template
import com.spacecay.expenselog.data.Txn
import com.spacecay.expenselog.data.Wallet
import com.spacecay.expenselog.util.Dates
import org.json.JSONArray
import org.json.JSONObject

/** Full JSON backup of every table (photos are not included). */
object Backup {
    private const val VERSION = 1

    // ---- tiny helpers ----
    private fun JSONObject.n(k: String, v: Long?) = put(k, v ?: JSONObject.NULL)
    private fun JSONObject.ln(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.l(k: String, d: Long = 0) = optLong(k, d)
    private fun JSONObject.i(k: String, d: Int = 0) = optInt(k, d)
    private fun JSONObject.s(k: String) = optString(k, "")
    private fun JSONObject.b(k: String, d: Boolean = false) = optBoolean(k, d)
    private fun <T> arr(list: List<T>, f: (T) -> JSONObject) = JSONArray().apply { list.forEach { put(f(it)) } }
    private fun <T> read(a: JSONArray?, f: (JSONObject) -> T): List<T> = if (a == null) emptyList() else (0 until a.length()).map { f(a.getJSONObject(it)) }

    suspend fun toJson(): String {
        val d = Graph.dao
        val root = JSONObject()
        root.put("app", "ExpenseLog"); root.put("version", VERSION); root.put("createdAt", System.currentTimeMillis())
        root.put("books", arr(d.allBooks()) { JSONObject().put("id", it.id).put("name", it.name).put("mainCurrency", it.mainCurrency).put("sortOrder", it.sortOrder) })
        root.put("rates", arr(d.allRates()) { JSONObject().put("bookId", it.bookId).put("code", it.code).put("rate", it.rate).put("updatedAt", it.updatedAt) })
        root.put("wallets", arr(d.allWallets()) {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("name", it.name).put("type", it.type).put("currency", it.currency)
                .put("initialBalance", it.initialBalance).put("color", it.color).put("icon", it.icon).put("archived", it.archived)
                .put("excludeFromTotal", it.excludeFromTotal).put("creditLimit", it.creditLimit).put("sortOrder", it.sortOrder)
        })
        root.put("categories", arr(d.allCategories()) {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("name", it.name).put("type", it.type).put("color", it.color)
                .put("icon", it.icon).n("parentId", it.parentId).put("sortOrder", it.sortOrder).put("archived", it.archived)
        })
        root.put("transactions", arr(d.allTxns()) {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("type", it.type).put("amount", it.amount).put("walletId", it.walletId)
                .n("toWalletId", it.toWalletId).n("toAmount", it.toAmount).put("fee", it.fee).n("categoryId", it.categoryId).put("note", it.note)
                .put("payee", it.payee).put("dateTime", it.dateTime).put("photos", it.photos).put("source", it.source).n("debtId", it.debtId)
                .n("goalId", it.goalId).n("recurringId", it.recurringId).put("ref", it.ref ?: JSONObject.NULL).put("reviewed", it.reviewed)
                .put("excludeFromStats", it.excludeFromStats).put("createdAt", it.createdAt).n("splitId", it.splitId)
        })
        root.put("budgets", arr(d.allBudgets()) {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("name", it.name).put("amount", it.amount).put("period", it.period)
                .put("startDate", it.startDate).n("endDate", it.endDate).put("categoryIds", it.categoryIds).put("walletIds", it.walletIds)
                .put("color", it.color).put("alertPercent", it.alertPercent)
        })
        root.put("goals", arr(d.allGoals()) {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("name", it.name).put("target", it.target).put("currency", it.currency)
                .n("deadline", it.deadline).put("color", it.color).put("icon", it.icon).put("reached", it.reached).put("note", it.note).put("createdAt", it.createdAt)
        })
        root.put("goalEntries", arr(d.allGoalEntries()) {
            JSONObject().put("id", it.id).put("goalId", it.goalId).put("amount", it.amount).put("date", it.date).put("note", it.note)
                .n("walletId", it.walletId).n("txnId", it.txnId)
        })
        root.put("debts", arr(d.allDebts()) {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("type", it.type).put("person", it.person).put("amount", it.amount)
                .put("currency", it.currency).put("startDate", it.startDate).n("dueDate", it.dueDate).put("note", it.note).put("color", it.color)
                .put("closed", it.closed).n("walletId", it.walletId).n("txnId", it.txnId)
        })
        root.put("debtEntries", arr(d.allDebtEntries()) {
            JSONObject().put("id", it.id).put("debtId", it.debtId).put("amount", it.amount).put("date", it.date).put("note", it.note)
                .n("walletId", it.walletId).n("txnId", it.txnId)
        })
        root.put("recurring", arr(d.allRecurring()) {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("type", it.type).put("amount", it.amount).put("walletId", it.walletId)
                .n("toWalletId", it.toWalletId).n("toAmount", it.toAmount).n("categoryId", it.categoryId).put("note", it.note)
                .put("frequency", it.frequency).put("interval", it.interval).put("startDate", it.startDate).put("nextDate", it.nextDate)
                .n("endDate", it.endDate).put("active", it.active).put("confirm", it.confirm)
        })
        root.put("templates", arr(d.allTemplates()) {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("name", it.name).put("type", it.type).put("amount", it.amount)
                .n("walletId", it.walletId).n("toWalletId", it.toWalletId).n("categoryId", it.categoryId).put("note", it.note).put("sortOrder", it.sortOrder)
        })
        root.put("rules", arr(d.allRules()) { JSONObject().put("id", it.id).put("bookId", it.bookId).put("keyword", it.keyword).put("categoryId", it.categoryId) })
        return root.toString()
    }

    // ---- Encryption (AES-256-GCM, key from password via PBKDF2) ----
    private fun key(password: String, salt: ByteArray): javax.crypto.SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val raw = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return javax.crypto.spec.SecretKeySpec(raw, "AES")
    }

    fun encrypt(plain: String, password: String): String {
        val rnd = java.security.SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, key(password, salt), javax.crypto.spec.GCMParameterSpec(128, iv))
        val data = c.doFinal(plain.toByteArray())
        fun b64(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
        return JSONObject().put("app", "ExpenseLog").put("encrypted", 1).put("salt", b64(salt)).put("iv", b64(iv)).put("data", b64(data)).toString()
    }

    fun isEncrypted(json: String): Boolean = try { JSONObject(json).optInt("encrypted", 0) == 1 } catch (e: Exception) { false }

    fun decrypt(json: String, password: String): String {
        val o = JSONObject(json)
        fun d(k: String) = android.util.Base64.decode(o.getString(k), android.util.Base64.NO_WRAP)
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.DECRYPT_MODE, key(password, d("salt")), javax.crypto.spec.GCMParameterSpec(128, d("iv")))
        return try { String(c.doFinal(d("data"))) } catch (e: Exception) { throw IllegalArgumentException("Wrong password") }
    }

    /** Replaces all data with the backup. Throws on invalid file. */
    suspend fun restore(json: String) {
        val r = JSONObject(json)
        require(r.optString("app") == "ExpenseLog") { "Not an Expense Log backup" }
        val d = Graph.dao
        Graph.db.withTransaction {
            d.wipeTxns(); d.wipeRates(); d.wipeWallets(); d.wipeCategories(); d.wipeBudgets(); d.wipeGoalEntries(); d.wipeGoals()
            d.wipeDebtEntries(); d.wipeDebts(); d.wipeRecurring(); d.wipeTemplates(); d.wipeRules(); d.wipeBooks()
            d.putBooks(read(r.optJSONArray("books")) { Book(it.l("id"), it.s("name"), it.s("mainCurrency"), it.i("sortOrder")) })
            d.upsertRates(read(r.optJSONArray("rates")) { CurrencyRate(it.l("bookId"), it.s("code"), it.optDouble("rate", 1.0), it.l("updatedAt")) })
            d.putWallets(read(r.optJSONArray("wallets")) {
                Wallet(it.l("id"), it.l("bookId"), it.s("name"), it.i("type"), it.s("currency"), it.l("initialBalance"), it.i("color"), it.s("icon"),
                    it.b("archived"), it.b("excludeFromTotal"), it.l("creditLimit"), it.i("sortOrder"))
            })
            d.putCategories(read(r.optJSONArray("categories")) {
                Category(it.l("id"), it.l("bookId"), it.s("name"), it.i("type"), it.i("color"), it.s("icon"), it.ln("parentId"), it.i("sortOrder"), it.b("archived"))
            })
            d.putTxns(read(r.optJSONArray("transactions")) {
                Txn(it.l("id"), it.l("bookId"), it.i("type"), it.l("amount"), it.l("walletId"), it.ln("toWalletId"), it.ln("toAmount"), it.l("fee"),
                    it.ln("categoryId"), it.s("note"), it.s("payee"), it.l("dateTime"), it.s("photos"), it.i("source"), it.ln("debtId"), it.ln("goalId"),
                    it.ln("recurringId"), if (it.isNull("ref")) null else it.optString("ref"), it.b("reviewed", true), it.b("excludeFromStats"), it.l("createdAt"), it.ln("splitId"))
            })
            d.putBudgets(read(r.optJSONArray("budgets")) {
                Budget(it.l("id"), it.l("bookId"), it.s("name"), it.l("amount"), it.i("period"), it.l("startDate"), it.ln("endDate"),
                    it.s("categoryIds"), it.s("walletIds"), it.i("color"), it.i("alertPercent", 80))
            })
            d.putGoals(read(r.optJSONArray("goals")) {
                Goal(it.l("id"), it.l("bookId"), it.s("name"), it.l("target"), it.s("currency"), it.ln("deadline"), it.i("color"), it.s("icon"),
                    it.b("reached"), it.s("note"), it.l("createdAt"))
            })
            d.putGoalEntries(read(r.optJSONArray("goalEntries")) {
                GoalEntry(it.l("id"), it.l("goalId"), it.l("amount"), it.l("date"), it.s("note"), it.ln("walletId"), it.ln("txnId"))
            })
            d.putDebts(read(r.optJSONArray("debts")) {
                Debt(it.l("id"), it.l("bookId"), it.i("type"), it.s("person"), it.l("amount"), it.s("currency"), it.l("startDate"), it.ln("dueDate"),
                    it.s("note"), it.i("color"), it.b("closed"), it.ln("walletId"), it.ln("txnId"))
            })
            d.putDebtEntries(read(r.optJSONArray("debtEntries")) {
                DebtEntry(it.l("id"), it.l("debtId"), it.l("amount"), it.l("date"), it.s("note"), it.ln("walletId"), it.ln("txnId"))
            })
            d.putRecurring(read(r.optJSONArray("recurring")) {
                Recurring(it.l("id"), it.l("bookId"), it.i("type"), it.l("amount"), it.l("walletId"), it.ln("toWalletId"), it.ln("toAmount"),
                    it.ln("categoryId"), it.s("note"), it.i("frequency"), it.i("interval", 1), it.l("startDate"), it.l("nextDate"), it.ln("endDate"), it.b("active", true), it.b("confirm"))
            })
            d.putTemplates(read(r.optJSONArray("templates")) {
                Template(it.l("id"), it.l("bookId"), it.s("name"), it.i("type"), it.l("amount"), it.ln("walletId"), it.ln("toWalletId"),
                    it.ln("categoryId"), it.s("note"), it.i("sortOrder"))
            })
            d.putRules(read(r.optJSONArray("rules")) { PayeeRule(it.l("id"), it.l("bookId"), it.s("keyword"), it.l("categoryId")) })
        }
        Graph.repo.ensureSeeded()
        Graph.repo.refreshWidget()
    }

    fun fileName() = "ExpenseLog-backup-${Dates.fmt("yyyy-MM-dd-HHmm", System.currentTimeMillis())}.json"

    suspend fun writeTo(ctx: Context, uri: Uri) {
        val pw = Graph.prefs.backupPassword
        val json = toJson().let { if (!pw.isNullOrEmpty()) encrypt(it, pw) else it }
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } ?: error("Couldn't open file")
        Graph.prefs.lastBackup = System.currentTimeMillis()
    }

    fun readText(ctx: Context, uri: Uri): String =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Couldn't open file")

    /** Restore; [password] needed only for encrypted backups. */
    suspend fun restoreText(text: String, password: String?) {
        restore(if (isEncrypted(text)) decrypt(text, password ?: throw IllegalArgumentException("Password needed")) else text)
    }

    /** Write a dated backup into the chosen folder; keeps the newest 10. */
    suspend fun backupToFolder(ctx: Context): Boolean {
        val folder = Graph.prefs.backupFolder ?: return false
        val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(folder)) ?: return false
        if (!dir.canWrite()) return false
        val file = dir.createFile("application/json", fileName().removeSuffix(".json")) ?: return false
        writeTo(ctx, file.uri)
        dir.listFiles().filter { it.name?.startsWith("ExpenseLog-backup") == true }
            .sortedByDescending { it.lastModified() }.drop(10).forEach { it.delete() }
        return true
    }

    suspend fun autoBackupIfDue(ctx: Context) {
        val p = Graph.prefs
        val every = when (p.autoBackup) { 1 -> 86_400_000L; 2 -> 7 * 86_400_000L; else -> return }
        if (System.currentTimeMillis() - p.lastBackup < every) return
        backupToFolder(ctx)
    }
}
