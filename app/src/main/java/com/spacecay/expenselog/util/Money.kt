package com.spacecay.expenselog.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

data class CurrencyInfo(val code: String, val name: String, val symbol: String)

object Currencies {
    val all: List<CurrencyInfo> = listOf(
        CurrencyInfo("ETB", "Ethiopian Birr", "Br"),
        CurrencyInfo("USD", "US Dollar", "$"),
        CurrencyInfo("EUR", "Euro", "€"),
        CurrencyInfo("GBP", "British Pound", "£"),
        CurrencyInfo("KES", "Kenyan Shilling", "KSh"),
        CurrencyInfo("AED", "UAE Dirham", "AED"),
        CurrencyInfo("SAR", "Saudi Riyal", "SR"),
        CurrencyInfo("CNY", "Chinese Yuan", "¥"),
        CurrencyInfo("JPY", "Japanese Yen", "¥"),
        CurrencyInfo("INR", "Indian Rupee", "₹"),
        CurrencyInfo("CAD", "Canadian Dollar", "C$"),
        CurrencyInfo("AUD", "Australian Dollar", "A$"),
        CurrencyInfo("CHF", "Swiss Franc", "CHF"),
        CurrencyInfo("SEK", "Swedish Krona", "kr"),
        CurrencyInfo("NOK", "Norwegian Krone", "kr"),
        CurrencyInfo("DKK", "Danish Krone", "kr"),
        CurrencyInfo("TRY", "Turkish Lira", "₺"),
        CurrencyInfo("ZAR", "South African Rand", "R"),
        CurrencyInfo("NGN", "Nigerian Naira", "₦"),
        CurrencyInfo("EGP", "Egyptian Pound", "E£"),
        CurrencyInfo("UGX", "Ugandan Shilling", "USh"),
        CurrencyInfo("TZS", "Tanzanian Shilling", "TSh"),
        CurrencyInfo("RWF", "Rwandan Franc", "FRw"),
        CurrencyInfo("DJF", "Djiboutian Franc", "Fdj"),
        CurrencyInfo("SOS", "Somali Shilling", "Sh"),
        CurrencyInfo("SDG", "Sudanese Pound", "SDG"),
        CurrencyInfo("ERN", "Eritrean Nakfa", "Nfk"),
        CurrencyInfo("QAR", "Qatari Riyal", "QR"),
        CurrencyInfo("KWD", "Kuwaiti Dinar", "KD"),
        CurrencyInfo("BHD", "Bahraini Dinar", "BD"),
        CurrencyInfo("OMR", "Omani Rial", "OMR"),
        CurrencyInfo("ILS", "Israeli Shekel", "₪"),
        CurrencyInfo("RUB", "Russian Ruble", "₽"),
        CurrencyInfo("BRL", "Brazilian Real", "R$"),
        CurrencyInfo("MXN", "Mexican Peso", "MX$"),
        CurrencyInfo("KRW", "South Korean Won", "₩"),
        CurrencyInfo("SGD", "Singapore Dollar", "S$"),
        CurrencyInfo("HKD", "Hong Kong Dollar", "HK$"),
        CurrencyInfo("MYR", "Malaysian Ringgit", "RM"),
        CurrencyInfo("THB", "Thai Baht", "฿"),
        CurrencyInfo("IDR", "Indonesian Rupiah", "Rp"),
        CurrencyInfo("PHP", "Philippine Peso", "₱"),
        CurrencyInfo("PKR", "Pakistani Rupee", "Rs"),
        CurrencyInfo("BDT", "Bangladeshi Taka", "৳"),
        CurrencyInfo("NZD", "New Zealand Dollar", "NZ$"),
        CurrencyInfo("PLN", "Polish Zloty", "zł"),
        CurrencyInfo("CZK", "Czech Koruna", "Kč"),
        CurrencyInfo("HUF", "Hungarian Forint", "Ft"),
        CurrencyInfo("BTC", "Bitcoin", "₿"),
        CurrencyInfo("USDT", "Tether", "USDT"),
    )
    private val map = all.associateBy { it.code }
    fun info(code: String): CurrencyInfo = map[code] ?: CurrencyInfo(code, code, code)
    fun symbol(code: String) = info(code).symbol
}

object Money {
    private val sym = DecimalFormatSymbols(Locale.US)
    private val fmt = DecimalFormat("#,##0.00", sym)
    private val fmtShort = DecimalFormat("#,##0.#", sym)

    /** cents -> "1,234.50" */
    fun plain(cents: Long): String = fmt.format(cents / 100.0)

    /** cents -> "ETB 1,234.50" (code prefix reads best for birr). */
    fun format(cents: Long, code: String, showSign: Boolean = false): String {
        val sign = when {
            showSign && cents > 0 -> "+"
            cents < 0 -> "-"
            else -> ""
        }
        return "$sign${prefix(code)}${fmt.format(abs(cents) / 100.0)}"
    }

    fun prefix(code: String): String {
        if (code == "ETB") return "ETB "
        val s = Currencies.symbol(code)
        return if (s.length == 1) s else "$s "
    }

    /** Compact: 12.3K, 4.5M */
    fun compact(cents: Long): String {
        val v = abs(cents) / 100.0
        val s = when {
            v >= 1_000_000_000 -> fmtShort.format(v / 1_000_000_000) + "B"
            v >= 1_000_000 -> fmtShort.format(v / 1_000_000) + "M"
            v >= 10_000 -> fmtShort.format(v / 1_000) + "K"
            else -> DecimalFormat("#,##0", sym).format(v)
        }
        return if (cents < 0) "-$s" else s
    }

    /** "1234.5" -> 123450 cents, null on bad input. */
    fun parse(text: String): Long? {
        val clean = text.replace(",", "").replace(" ", "").trim()
        if (clean.isEmpty()) return null
        return try {
            BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
        } catch (e: Exception) {
            null
        }
    }

    /** cents -> editable text without grouping: "1234.5" */
    fun toInput(cents: Long): String {
        if (cents == 0L) return ""
        val bd = BigDecimal.valueOf(cents).movePointLeft(2).stripTrailingZeros()
        return bd.toPlainString()
    }

    fun convert(cents: Long, rate: Double): Long = (cents * rate).roundToLong()

    fun formatRate(rate: Double): String {
        val bd = BigDecimal(rate).setScale(if (rate < 1) 6 else 4, RoundingMode.HALF_UP).stripTrailingZeros()
        val s = bd.toPlainString()
        return if (s.contains('.')) s else "$s.00"
    }
}
