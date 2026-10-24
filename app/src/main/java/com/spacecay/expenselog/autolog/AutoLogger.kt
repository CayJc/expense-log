package com.spacecay.expenselog.autolog

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.TxSource
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.data.Txn
import com.spacecay.expenselog.data.WalletType
import com.spacecay.expenselog.ui.Prefill
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.util.Photos
import com.spacecay.expenselog.work.Notifier
import kotlinx.coroutines.tasks.await

object AutoLogger {

    suspend fun walletFor(provider: Provider): Long? {
        val prefs = Graph.prefs
        val wallets = Graph.dao.getWallets(prefs.bookId.value).filter { !it.archived }
        val wanted = when (provider) {
            Provider.TELEBIRR -> prefs.telebirrWalletId
            Provider.CBE -> prefs.cbeWalletId
            Provider.OTHER -> 0L
        }
        wallets.firstOrNull { it.id == wanted }?.let { return it.id }
        val byName = when (provider) {
            Provider.TELEBIRR -> wallets.firstOrNull { it.name.contains("tele", true) } ?: wallets.firstOrNull { it.type == WalletType.MOBILE_MONEY }
            Provider.CBE -> wallets.firstOrNull { it.name.contains("cbe", true) } ?: wallets.firstOrNull { it.type == WalletType.BANK }
            Provider.OTHER -> null
        }
        return (byName ?: wallets.firstOrNull())?.id
    }

    /** True if this payment is already in the log. */
    suspend fun isDuplicate(p: ParsedPayment, bookId: Long): Boolean {
        val dao = Graph.dao
        if (p.ref != null && dao.countByRef(bookId, p.ref) > 0) return true
        val t = p.time ?: System.currentTimeMillis()
        val window = if (p.time != null) 2 * 60_000L else 10 * 60_000L
        return p.ref == null && dao.countSimilar(bookId, p.amount, p.type, t - window, t + window) > 0
    }

    /** Write a parsed payment as a transaction (unreviewed). Returns new id, or null if skipped. */
    suspend fun log(ctx: Context, p: ParsedPayment, source: Int, photo: String? = null, notify: Boolean = true): Long? {
        val bookId = Graph.prefs.bookId.value
        if (isDuplicate(p, bookId)) return null
        val walletId = walletFor(p.provider) ?: return null
        val cat = Graph.repo.guessCategory(bookId, p.payee + " " + p.raw.take(300), p.type)
        val note = when (p.provider) {
            Provider.TELEBIRR -> "telebirr"
            Provider.CBE -> "CBE"
            else -> ""
        }
        val id = Graph.repo.saveTxn(
            Txn(
                bookId = bookId, type = p.type, amount = p.amount, walletId = walletId,
                fee = if (Graph.prefs.logFees && p.type == TxType.EXPENSE) p.fee else 0L,
                categoryId = cat, note = note, payee = p.payee, dateTime = p.time ?: System.currentTimeMillis(),
                photos = photo ?: "", source = source, ref = p.ref, reviewed = false,
            )
        )
        if (notify) Notifier.logged(ctx, id, p)
        Notifier.checkBudgets(ctx, bookId)
        return id
    }

    suspend fun ocr(ctx: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(ctx, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            recognizer.process(image).await().text
        } finally {
            recognizer.close()
        }
    }

    fun looksLikePayment(text: String): Boolean {
        val l = text.lowercase()
        val money = "etb" in l || "birr" in l || "br " in l
        val action = listOf("transaction", "transfer", "paid", "payment", "success", "debited", "credited", "received", "sent").any { it in l }
        return money && action
    }

    /** For share intents: turn an image/text into a prefill for the add screen. */
    suspend fun prefillFromImage(ctx: Context, uri: Uri): Prefill {
        val text = try { ocr(ctx, uri) } catch (e: Exception) { "" }
        val p = PaymentParser.parse(text, null, fromScreenshot = true)
        return toPrefill(p, TxSource.SCREENSHOT).copy(photoUri = uri.toString())
    }

    suspend fun prefillFromText(text: String): Prefill = toPrefill(PaymentParser.parse(text), TxSource.SHARED_TEXT)

    private suspend fun toPrefill(p: ParsedPayment?, source: Int): Prefill {
        if (p == null) return Prefill(TxType.EXPENSE, 0L, source = source)
        val bookId = Graph.prefs.bookId.value
        return Prefill(
            type = p.type, amount = p.amount, walletId = walletFor(p.provider),
            categoryId = Graph.repo.guessCategory(bookId, p.payee + " " + p.raw.take(300), p.type),
            note = when (p.provider) { Provider.TELEBIRR -> "telebirr"; Provider.CBE -> "CBE"; else -> "" },
            payee = p.payee, dateTime = p.time, fee = if (p.type == TxType.EXPENSE) p.fee else 0L, ref = p.ref, source = source,
        )
    }

    fun describe(p: ParsedPayment): String =
        (if (p.type == TxType.INCOME) "Received " else "Paid ") + Money.format(p.amount, "ETB") +
            (if (p.payee.isNotBlank()) (if (p.type == TxType.INCOME) " from " else " to ") + p.payee else "")

    /** Keep a copy of the screenshot with the transaction. */
    fun keepPhoto(ctx: Context, uri: Uri): String? = Photos.import(ctx, uri)
}
