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
import com.spacecay.expenselog.data.Wallet
import com.spacecay.expenselog.ui.Prefill
import com.spacecay.expenselog.util.Money
import com.spacecay.expenselog.util.Palette
import com.spacecay.expenselog.util.Photos
import com.spacecay.expenselog.work.Notifier
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

object AutoLogger {

    /**
     * The wallet a provider's payments go into. Uses the user's choice, then a wallet with a
     * matching name/currency, and otherwise creates one (e.g. "Apollo · USD") so nothing is lost.
     */
    suspend fun walletFor(provider: Provider, currency: String = "ETB", create: Boolean = true): Long? {
        val prefs = Graph.prefs
        val bookId = prefs.bookId.value
        val wallets = Graph.dao.getWallets(bookId).filter { !it.archived }
        val chosen = wallets.firstOrNull { it.id == prefs.providerWallet(provider.name) }
        if (chosen != null && (chosen.currency == currency || !create)) return chosen.id

        val keys = providerKeys(provider)
        val named = wallets.filter { w -> keys.any { w.name.contains(it, ignoreCase = true) } }
        named.firstOrNull { it.currency == currency }?.let { return it.id }
        if (provider == Provider.OTHER) return chosen?.id ?: wallets.firstOrNull()?.id
        if (!create) return (chosen ?: named.firstOrNull())?.id

        // Create the wallet so the payment has a home; the user can rename or merge later.
        val book = Graph.dao.getBook(bookId)
        val main = book?.mainCurrency ?: "ETB"
        val name = provider.walletName + if (currency != "ETB") " · $currency" else ""
        val id = Graph.dao.insertWallet(
            Wallet(bookId = bookId, name = name, type = provider.walletType, currency = currency,
                color = Palette.at(provider.ordinal * 3 + 1), icon = provider.walletIcon, sortOrder = wallets.size)
        )
        if (currency != main && Graph.dao.getRates(bookId).none { it.code == currency }) Graph.repo.setRate(bookId, currency, 1.0)
        if (chosen == null && currency == "ETB") prefs.setProviderWallet(provider.name, id)
        return id
    }

    fun providerKeys(provider: Provider): List<String> = when (provider) {
        Provider.TELEBIRR -> listOf("tele")
        Provider.MPESA -> listOf("m-pesa", "mpesa")
        Provider.CBE -> listOf("cbe", "commercial")
        Provider.BOA -> listOf("abyssinia", "boa")
        Provider.APOLLO -> listOf("apollo")
        Provider.OTHER -> emptyList()
    }

    private val logLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Finds an already-logged copy of this payment: same reference, or same direction within a few
     * minutes with the same amount — or, in the same wallet, an amount that differs only by a fee
     * (telebirr notifications say 15.00 while the receipt says 16.00 including the 1.00 fee).
     * If found, missing details (reference, payee, fee, photo) are added to it and true is returned.
     */
    suspend fun isDuplicate(p: ParsedPayment, bookId: Long, photo: (() -> String?)? = null, source: Int = -1): Boolean {
        val dao = Graph.dao
        if (p.ref != null && dao.countByRef(bookId, p.ref) > 0) return true
        val t = p.time ?: System.currentTimeMillis()
        val window = if (p.time != null) 3 * 60_000L else 10 * 60_000L
        val walletId = walletFor(p.provider, p.currency, create = false)
        val near = dao.findNear(bookId, p.type, t - window, t + window)
            .filter { it.source != TxSource.MANUAL || it.ref != null }
            .filter { c -> p.ref == null || c.ref == null || c.ref == p.ref }
        val match = near.filter { it.amount == p.amount }.minByOrNull { abs(it.dateTime - t) }
            ?: near.filter { c ->
                // Fee-sized difference only counts between different channels (notification vs receipt),
                // so two real payments of 10 and 11 birr from the same channel stay separate.
                val small = minOf(c.amount, p.amount)
                val sameWallet = walletId == null || c.walletId == walletId
                val feeSized = c.amount != p.amount && abs(c.amount - p.amount) <= maxOf(small * 7 / 100, 100L)
                val otherChannel = source == -1 || c.source != source
                sameWallet && otherChannel && (feeSized || (c.fee > 0 && c.amount + c.fee == p.amount))
            }.minByOrNull { abs(it.dateTime - t) }
            ?: return false

        // Enrich the existing entry with whatever the new copy knows.
        var updated = match
        if (p.ref != null && match.ref == null) updated = updated.copy(ref = p.ref)
        if (match.payee.isBlank() && p.payee.isNotBlank()) updated = updated.copy(payee = p.payee)
        if (p.amount > match.amount && match.fee == 0L && p.type == TxType.EXPENSE) {
            // Bigger figure = total including fee: keep the base amount, record the difference as fee.
            updated = updated.copy(fee = p.amount - match.amount)
        } else if (p.amount < match.amount && match.fee == 0L && p.type == TxType.EXPENSE) {
            updated = updated.copy(amount = p.amount, fee = match.amount - p.amount)
        }
        if (match.photos.isBlank()) photo?.invoke()?.let { updated = updated.copy(photos = it) }
        if (updated != match) Graph.repo.saveTxn(updated)
        return true
    }

    /** Write a parsed payment as a transaction (unreviewed). Returns new id, or null if skipped. */
    suspend fun log(ctx: Context, p: ParsedPayment, source: Int, photo: (() -> String?)? = null, notify: Boolean = true): Long? =
        logLock.withLock { logLocked(ctx, p, source, photo, notify) }

    private suspend fun logLocked(ctx: Context, p: ParsedPayment, source: Int, photoFn: (() -> String?)?, notify: Boolean): Long? {
        val bookId = Graph.prefs.bookId.value
        if (isDuplicate(p, bookId, photoFn, source)) return null
        val photo = photoFn?.invoke()
        val walletId = walletFor(p.provider, p.currency) ?: return null
        val wallet = Graph.dao.getWallet(walletId) ?: return null

        // Payment in another currency than the wallet: convert with the current rate and note it.
        var amount = p.amount
        var fee = if (Graph.prefs.logFees && p.type == TxType.EXPENSE) p.fee else 0L
        var note = p.provider.label.takeIf { p.provider != Provider.OTHER }.orEmpty()
        if (wallet.currency != p.currency) {
            val rates = Graph.repo.rates(bookId)
            amount = rates.convert(p.amount, p.currency, wallet.currency)
            fee = rates.convert(fee, p.currency, wallet.currency)
            note = listOf(note, "${p.currency} ${Money.plain(p.amount)}").filter { it.isNotBlank() }.joinToString(" · ")
        }
        if (p.selfTransfer) note = listOf(note, "Transfer between your accounts — edit to pick the other wallet").filter { it.isNotBlank() }.joinToString(" · ")

        // Own-account move: if the provider has exactly one other wallet (e.g. Apollo USD → Apollo ETB), log a transfer.
        if (p.selfTransfer) {
            val keys = providerKeys(p.provider)
            val others = Graph.dao.getWallets(bookId).filter { w -> !w.archived && w.id != walletId && keys.any { w.name.contains(it, ignoreCase = true) } }
            if (others.size == 1) {
                val to = others.first()
                val rates = Graph.repo.rates(bookId)
                val id = Graph.repo.saveTxn(
                    Txn(bookId = bookId, type = TxType.TRANSFER, amount = amount, walletId = walletId, toWalletId = to.id,
                        toAmount = if (to.currency != wallet.currency) rates.convert(amount, wallet.currency, to.currency) else null,
                        fee = fee, note = p.provider.label, payee = "", dateTime = p.time ?: System.currentTimeMillis(),
                        photos = photo ?: "", source = source, ref = p.ref, reviewed = false)
                )
                if (notify) Notifier.logged(ctx, id, p)
                return id
            }
        }

        val cat = if (p.selfTransfer) null else guessCategory(bookId, p.payee, p.raw, p.type, amount, p.time ?: System.currentTimeMillis())
        val id = Graph.repo.saveTxn(
            Txn(
                bookId = bookId, type = p.type, amount = amount, walletId = walletId, fee = fee,
                categoryId = cat, note = note, payee = p.payee, dateTime = p.time ?: System.currentTimeMillis(),
                photos = photo ?: "", source = source, ref = p.ref, reviewed = false, excludeFromStats = p.selfTransfer,
            )
        )
        if (notify) Notifier.logged(ctx, id, p)
        Notifier.checkBudgets(ctx, bookId)
        if (p.balance != null && wallet.currency == p.currency) checkBalance(ctx, wallet, p.balance)
        return id
    }

    /** Compare the balance printed in the message with the wallet's balance in the app. */
    private suspend fun checkBalance(ctx: Context, wallet: Wallet, reported: Long) {
        val delta = Graph.dao.getWalletDeltas(wallet.bookId).firstOrNull { it.walletId == wallet.id }?.delta ?: 0L
        val inApp = wallet.initialBalance + delta
        val diff = reported - inApp
        Graph.prefs.setBalanceCheck(wallet.id, reported, System.currentTimeMillis())
        if (abs(diff) >= 100) Notifier.balanceMismatch(ctx, wallet, reported, inApp)
    }

    /**
     * Category guess, best first: learned payee rule → what you picked for this payee before →
     * built-in keywords → your habit for similar amounts at a similar time of day.
     */
    suspend fun guessCategory(bookId: Long, payee: String, raw: String, type: Int, amount: Long, time: Long): Long? {
        Graph.repo.guessCategory(bookId, "$payee ${raw.take(300)}", type, rulesOnly = true)?.let { return it }
        if (payee.isNotBlank()) Graph.dao.topCategoryForPayee(bookId, payee)?.let { return it }
        Graph.repo.guessCategory(bookId, "$payee ${raw.take(300)}", type)?.let { return it }
        return habitCategory(bookId, type, amount, time)
    }

    suspend fun habitCategory(bookId: Long, type: Int, amount: Long, time: Long): Long? {
        if (amount <= 0) return null
        val since = time - 90L * 86_400_000L
        val list = Graph.dao.similarAmounts(bookId, type, (amount * 0.8).toLong(), (amount * 1.25).toLong(), since)
        val hour = com.spacecay.expenselog.util.Dates.cal(time).get(java.util.Calendar.HOUR_OF_DAY)
        val near = list.filter {
            val h = com.spacecay.expenselog.util.Dates.cal(it.dateTime).get(java.util.Calendar.HOUR_OF_DAY)
            abs(h - hour) <= 2 || abs(h - hour) >= 22
        }
        val best = near.groupingBy { it.categoryId }.eachCount().maxByOrNull { it.value } ?: return null
        return if (best.value >= 3 && best.value * 2 >= near.size) best.key else null
    }

    /**
     * On-device OCR that keeps the receipt layout: words on the same row are joined as
     * "Label : Value" so receipts like Bank of Abyssinia's two-column table read correctly.
     */
    suspend fun ocr(ctx: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(ctx, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = recognizer.process(image).await()
            val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { it to l.text } }
            if (lines.isEmpty()) return result.text
            val sorted = lines.sortedBy { it.first.centerY() }
            val rows = mutableListOf<MutableList<Pair<android.graphics.Rect, String>>>()
            for (l in sorted) {
                val row = rows.lastOrNull()
                val ref = row?.first()?.first
                if (row != null && ref != null && abs(l.first.centerY() - ref.centerY()) < maxOf(ref.height(), l.first.height()) * 0.6) row.add(l)
                else rows.add(mutableListOf(l))
            }
            rows.joinToString("\n") { r -> r.sortedBy { it.first.left }.joinToString(" : ") { it.second } } + "\n" + result.text
        } finally {
            recognizer.close()
        }
    }

    fun looksLikePayment(text: String): Boolean {
        val l = text.lowercase()
        val money = "etb" in l || "birr" in l || "br " in l || "usd" in l
        val action = listOf("transaction", "transfer", "paid", "payment", "success", "debited", "credited", "received", "sent", "amount").any { it in l }
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
        val walletId = walletFor(p.provider, p.currency)
        val wallet = walletId?.let { Graph.dao.getWallet(it) }
        val amount = if (wallet != null && wallet.currency != p.currency) Graph.repo.rates(bookId).convert(p.amount, p.currency, wallet.currency) else p.amount
        return Prefill(
            type = p.type, amount = amount, walletId = walletId,
            categoryId = if (p.selfTransfer) null else guessCategory(bookId, p.payee, p.raw, p.type, amount, p.time ?: System.currentTimeMillis()),
            note = p.provider.label.takeIf { p.provider != Provider.OTHER }.orEmpty(),
            payee = p.payee, dateTime = p.time, fee = if (p.type == TxType.EXPENSE) p.fee else 0L, ref = p.ref, source = source,
        )
    }

    fun describe(p: ParsedPayment): String =
        (if (p.type == TxType.INCOME) "Received " else "Paid ") + Money.format(p.amount, p.currency) +
            (if (p.payee.isNotBlank()) (if (p.type == TxType.INCOME) " from " else " to ") + p.payee else "") +
            (if (p.provider != Provider.OTHER) " · ${p.provider.label}" else "")

    /** Keep a copy of the screenshot with the transaction. */
    fun keepPhoto(ctx: Context, uri: Uri): String? = Photos.import(ctx, uri)
}
