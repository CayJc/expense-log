package com.spacecay.expenselog.autolog

import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.data.WalletType
import com.spacecay.expenselog.util.Money
import java.util.Calendar
import kotlin.math.abs

/** Payment services the app recognises. [walletName] is used when a wallet has to be created. */
enum class Provider(val label: String, val walletName: String, val walletType: Int, val walletIcon: String) {
    TELEBIRR("telebirr", "telebirr", WalletType.MOBILE_MONEY, "smartphone"),
    MPESA("M-PESA", "M-PESA", WalletType.MOBILE_MONEY, "smartphone"),
    CBE("CBE", "CBE", WalletType.BANK, "bank"),
    BOA("Bank of Abyssinia", "Abyssinia", WalletType.BANK, "bank"),
    APOLLO("Apollo", "Apollo", WalletType.BANK, "bank"),
    OTHER("Other", "Bank", WalletType.BANK, "bank"),
}

data class ParsedPayment(
    val type: Int,
    val amount: Long,
    val fee: Long,
    val payee: String,
    val ref: String?,
    val balance: Long?,
    val time: Long?,
    val provider: Provider,
    val raw: String,
    val currency: String = "ETB",
    /** Money moved between two of your own accounts (same name on both sides). */
    val selfTransfer: Boolean = false,
)

/**
 * Reads telebirr, M-PESA, CBE, Bank of Abyssinia and Apollo payment messages, notifications
 * and OCR'd receipt screens. Works on loose text so it survives wording changes.
 */
object PaymentParser {
    private const val NUM = "([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    private const val CUR = "(ETB|Birr|BIRR|Br\\.?|USD|US\\$|EUR|\\$)"

    private val amountAfter = Regex("$CUR\\s*:?\\s*-?\\s*$NUM", RegexOption.IGNORE_CASE)
    private val amountBefore = Regex("-?\\s*$NUM\\s*\\(?\\s*$CUR(?![a-z])", RegexOption.IGNORE_CASE)

    private val incomeWords = listOf(
        "you have received", "received", "has been credited", "credited", "deposited", "cash in", "refund", "reversal", "transferred to you", "sent you",
    )
    private val expenseWords = listOf(
        "you have transferred", "transferred", "you have paid", "paid", "has been debited", "debited", "you have sent", "sent", "withdrawn",
        "withdraw", "cash out", "bought", "purchased", "purchase", "payment", "transfer to", "airtime", "package", "topup", "top up", "top-up",
    )
    private val successWords = listOf("successful", "success", "completed", "transaction details", "receipt", "source account", "transaction reference")

    private val totalRx = Regex("total\\s*amount\\s*(?:debited|paid)?\\s*:?\\s*(?:ETB|Birr)?\\s*$NUM", RegexOption.IGNORE_CASE)
    private val forAmountRx = Regex("(?:success\\s*for|amount\\s*of|amount|amt)\\s*:?\\s*-?\\s*$NUM", RegexOption.IGNORE_CASE)
    private val feeRx = Regex("(?:service\\s*fee|transaction\\s*fee|\\bfee|service\\s*charge)[^0-9]{0,30}?$NUM", RegexOption.IGNORE_CASE)
    private val vatRx = Regex("(?:VAT)[^0-9]{0,30}?$NUM", RegexOption.IGNORE_CASE)
    private val balanceRx = Regex("balance[^0-9]{0,40}?$NUM", RegexOption.IGNORE_CASE)
    private val refRx = Regex(
        "(?:transaction\\s*(?:number|no\\.?|id|ref(?:erence)?)\\b|trans\\.?\\s*id|txn\\s*id|\\bref(?:erence)?\\s*(?:no\\.?|number|id)?\\b|receipt\\s*(?:no\\.?|number))\\s*(?:is)?\\s*[:#.\\-]?\\s*([A-Z0-9]{6,24})",
        RegexOption.IGNORE_CASE,
    )
    private val ftRx = Regex("\\b(FT[0-9A-Z]{8,})\\b")
    /** 24/10/2026 13:45:22 · 24/10/26 at 10:16 PM · 24-10-2026, 22:21:30 */
    private val dateRx1 = Regex("(?<!\\d)(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})\\s*(?:,|at|T)?\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*([AaPp][Mm])?")
    /** 2026/10/24 14:22:10 */
    private val dateRx2 = Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})[ T,]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")

    private val srcNameRx = Regex("source\\s*account\\s*name\\s*[:\\-]?\\s*([A-Za-z][A-Za-z .'\\-]{2,60})", RegexOption.IGNORE_CASE)
    private val dstNameRx = Regex("(?:destination|receiver|beneficiary|credited)\\s*account\\s*name\\s*[:\\-]?\\s*([A-Za-z][A-Za-z .'\\-]{2,60})", RegexOption.IGNORE_CASE)

    private val payeePatterns = listOf(
        // 0: labelled fields on receipts
        Regex("(?:transaction\\s*to|destination\\s*account\\s*name|credited\\s*party(?:\\s*name)?|receiver(?:\\s*name)?|recipient(?:\\s*name)?|beneficiary(?:\\s*name)?|merchant(?:\\s*name)?|paid\\s*to)\\s*[:\\-]?\\s*([A-Za-z][A-Za-z .&'\\-]{2,40})", RegexOption.IGNORE_CASE),
        // 1
        Regex("(?:goods\\s+purchased\\s+from|purchased\\s+from|paid\\s+for\\s+goods\\s+from)\\s+(?:[0-9]+\\s*-\\s*)?([^.,\\n]{2,40}?)(?=\\s+on\\s|\\s*[.,(]|$)", RegexOption.IGNORE_CASE),
        // 2
        Regex("\\bto\\s+([A-Za-z][^.,\\n(]{1,40}?)(?=\\s*\\(|\\s+on\\s|\\s+at\\s|\\s+account|\\s+with\\s|\\s*[.,]|$)", RegexOption.IGNORE_CASE),
        // 3
        Regex("\\bfrom\\s+([A-Za-z][^.,\\n(]{1,40}?)(?=\\s*\\(|\\s+on\\s|\\s+at\\s|\\s+account|\\s+with\\s|\\s*[.,]|$)", RegexOption.IGNORE_CASE),
        // 4
        Regex("(?:sender(?:\\s*name)?|debited\\s*party|from\\s*account\\s*name)\\s*[:\\-]?\\s*([A-Za-z][A-Za-z .&'\\-]{2,40})", RegexOption.IGNORE_CASE),
        // 5
        Regex("\\bfor\\s+(?!goods|airtime|the\\b|your\\b)([A-Za-z][A-Za-z .'\\-]{2,40}?)(?=\\s*\\n|\\s+on\\s|\\s*[.,(]|$)", RegexOption.IGNORE_CASE),
        // 6: "bought a Safaricom bundle of 5.00" / "bought a package Daily Data Package worth 5.00"
        Regex("bought\\s+(?:a\\s+)?(?:package\\s+)?([A-Za-z][A-Za-z .'\\-]{2,40}?)\\s+(?:of|worth|for)\\b", RegexOption.IGNORE_CASE),
        // 7: receipt "Transaction Type : Ethiotelecom Topup"
        Regex("transaction\\s*type\\s*[:\\-]?\\s*([A-Za-z][A-Za-z .&'\\-]{2,40})", RegexOption.IGNORE_CASE),
    )

    private val reject = listOf(
        "otp", "verification code", "one time password", "pin code", "do not share", "failed", "unsuccessful", "insufficient",
        "cancelled", "canceled", "declined", "not successful",
    )

    /** SMS senders whose messages are payment messages. Matched against the notification title. */
    val trustedSenders = listOf("mpesa", "m-pesa", "telebirr", "127", "cbe", "boa", "abyssinia", "apollo", "830", "8397")

    fun detectProvider(text: String, pkg: String?): Provider {
        val l = text.lowercase()
        val p = pkg?.lowercase().orEmpty()
        return when {
            "apollo" in l || "apollo" in p -> Provider.APOLLO
            "abyssinia" in l || "boa" in p || "abyssinia" in p || Regex("\\bboa\\b").containsMatchIn(l) -> Provider.BOA
            "m-pesa" in l || "mpesa" in l || "mpesa" in p || "safaricom" in p -> Provider.MPESA
            "telebirr" in l || "ethiopay" in p || "telebirr" in p || "e-money" in l -> Provider.TELEBIRR
            "cbe" in l || "commercial bank" in l || p.contains("cbe") || p.contains("combanketh") -> Provider.CBE
            ftRx.containsMatchIn(text) -> Provider.CBE
            "ethio telecom" in l -> Provider.TELEBIRR
            else -> Provider.OTHER
        }
    }

    /**
     * [trusted] = text came from a known payment app / sender; their messages are sometimes as
     * short as "Success for -5.00", so we accept those as money out.
     */
    fun parse(input: String, pkg: String? = null, fromScreenshot: Boolean = false, trusted: Boolean = false): ParsedPayment? {
        val text = input.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val lower = text.lowercase()
        if (text.length < 8) return null
        if (reject.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) }) return null

        val provider = detectProvider(text, pkg)

        val srcName = srcNameRx.find(input)?.groupValues?.get(1)?.let { clean(it) }
        val dstName = dstNameRx.find(input)?.groupValues?.get(1)?.let { clean(it) }
        val selfTransfer = srcName != null && dstName != null && srcName.isNotBlank() && srcName.equals(dstName, ignoreCase = true)

        // Direction: whichever keyword appears first.
        fun firstIdx(words: List<String>) = words.mapNotNull { w -> Regex("\\b${Regex.escape(w)}").find(lower)?.range?.first }.minOrNull()
        val inc = firstIdx(incomeWords)
        val exp = firstIdx(expenseWords)
        var type = when {
            inc != null && (exp == null || inc < exp) -> TxType.INCOME
            exp != null -> TxType.EXPENSE
            else -> -1
        }
        // A receipt you're the source of is money out.
        if (type == -1 && srcName != null) type = TxType.EXPENSE
        // A success screen with a leading minus is money out; plus is money in.
        if (type == -1 && fromScreenshot) {
            type = when {
                Regex("\\+\\s*$NUM").containsMatchIn(text) && successWords.any { it in lower } -> TxType.INCOME
                successWords.any { it in lower } || Regex("-\\s*$NUM").containsMatchIn(text) -> TxType.EXPENSE
                else -> -1
            }
        }
        if (type == -1 && trusted) type = TxType.EXPENSE
        if (type == -1) return null

        val picked = pickAmount(text) ?: forAmountRx.find(text)?.let { m -> Money.parse(m.groupValues[1])?.let { it to "ETB" } } ?: return null
        val (amount, currency) = picked
        if (amount <= 0) return null

        val fee = (feeRx.find(text)?.let { Money.parse(it.groupValues[1]) } ?: 0L) + (vatRx.find(text)?.let { Money.parse(it.groupValues[1]) } ?: 0L)
        val total = totalRx.find(text)?.let { Money.parse(it.groupValues[1]) }
        val fee2 = if (fee == 0L && total != null && total > amount && total - amount < amount) total - amount else fee
        val balance = balanceRx.find(text)?.let { Money.parse(it.groupValues[1]) }
        val ref = ftRx.find(text)?.groupValues?.get(1)
            ?: refRx.findAll(text).map { it.groupValues[1].uppercase() }.firstOrNull { r -> r.any { it.isLetter() } && r.any { it.isDigit() } }
            ?: refRx.find(text)?.groupValues?.get(1)?.uppercase()?.takeIf { it.length >= 8 && it.any { c -> c.isDigit() } }
        val payee = when {
            selfTransfer -> "Own account"
            else -> findPayee(input, type).ifBlank { findPayee(text, type) }
                .ifBlank { Regex("transaction\\s*type\\s*[:\\-]?\\s*([A-Za-z][A-Za-z .&'\\-]{2,40})", RegexOption.IGNORE_CASE).find(input)?.groupValues?.get(1)?.let { clean(it) }.orEmpty() }
                .ifBlank { if ("airtime" in lower) "Airtime" else if ("package" in lower || "bundle" in lower) "Package" else "" }
        }
        val time = parseTime(text)

        return ParsedPayment(type, amount, fee2.coerceAtMost(amount), payee, ref, balance, time, provider, input.take(1500), currency, selfTransfer)
    }

    private fun currencyOf(token: String): String {
        val t = token.uppercase().trim('.')
        return when {
            t.startsWith("US") || t == "$" -> "USD"
            t == "EUR" -> "EUR"
            else -> "ETB"
        }
    }

    /** First currency amount that isn't a fee, VAT or balance figure. */
    private fun pickAmount(text: String): Pair<Long, String>? {
        val candidates = (amountAfter.findAll(text).map { Triple(it.range.first, it.groupValues[2], it.groupValues[1]) } +
            amountBefore.findAll(text).map { Triple(it.range.first, it.groupValues[1], it.groupValues[2]) })
            .sortedBy { it.first }
            .toList()
        for ((pos, num, cur) in candidates) {
            val before = text.substring(maxOf(0, pos - 32), pos).lowercase()
            if ("balance" in before || "fee" in before || "vat" in before || "charge" in before || "limit" in before) continue
            val v = Money.parse(num) ?: continue
            if (v > 0) return v to currencyOf(cur)
        }
        // Screenshot fallback: largest standalone decimal number
        return Regex("(?<![0-9])-?$NUM(?![0-9])").findAll(text)
            .mapNotNull { m -> m.groupValues[1].takeIf { it.contains('.') }?.let { Money.parse(it) } }
            .filter { it in 100..100_000_000_00L }
            .maxOrNull()?.let { it to "ETB" }
    }

    private fun findPayee(text: String, type: Int): String {
        val order = if (type == TxType.INCOME) listOf(4, 3, 0, 1) else listOf(0, 6, 7, 1, 2, 5)
        for (i in order) {
            val m = payeePatterns[i].find(text) ?: continue
            val v = clean(m.groupValues[1])
            val lv = v.lowercase()
            if (v.length >= 2 && !lv.startsWith("your") && !lv.startsWith("account") && !lv.startsWith("number") && lv != "all") return v
        }
        return ""
    }

    private fun clean(s: String): String = s
        .replace(Regex("[0-9*]{4,}"), "")
        .replace(Regex("\\b(ETB|Birr|on|at)\\b.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', ':', '.', ',')
        .take(40)

    private fun parseTime(text: String): Long? {
        val c = Calendar.getInstance()
        val t = dateRx1.find(text)?.let { m ->
            val g = m.groupValues
            var y = g[3].toInt(); if (y < 100) y += 2000
            var h = g[4].toInt()
            val ampm = g[7].lowercase()
            if (ampm == "pm" && h < 12) h += 12
            if (ampm == "am" && h == 12) h = 0
            c.set(y, g[2].toInt() - 1, g[1].toInt(), h, g[5].toInt(), g[6].toIntOrNull() ?: 0); c.set(Calendar.MILLISECOND, 0); c.timeInMillis
        } ?: dateRx2.find(text)?.let { m ->
            val (y, mo, d, h, mi) = m.destructured
            c.set(y.toInt(), mo.toInt() - 1, d.toInt(), h.toInt(), mi.toInt(), m.groupValues[6].toIntOrNull() ?: 0); c.set(Calendar.MILLISECOND, 0); c.timeInMillis
        } ?: return null
        // Only trust dates near "now" (avoids dd/mm vs mm/dd surprises).
        return if (abs(t - System.currentTimeMillis()) < 5L * 86_400_000L) t else null
    }
}
