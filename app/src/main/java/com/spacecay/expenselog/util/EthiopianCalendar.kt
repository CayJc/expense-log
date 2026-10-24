package com.spacecay.expenselog.util

import java.util.Calendar

/** Gregorian → Ethiopian (Amete Mihret) date conversion via Julian Day Numbers. */
object EthiopianCalendar {
    data class EthDate(val year: Int, val month: Int, val day: Int)

    val months = listOf("Meskerem", "Tikimt", "Hidar", "Tahsas", "Tir", "Yekatit", "Megabit", "Miyazya", "Ginbot", "Sene", "Hamle", "Nehase", "Pagume")
    val monthsAm = listOf("መስከረም", "ጥቅምት", "ኅዳር", "ታኅሣሥ", "ጥር", "የካቲት", "መጋቢት", "ሚያዝያ", "ግንቦት", "ሰኔ", "ሐምሌ", "ነሐሴ", "ጳጉሜ")

    private const val EPOCH = 1723856L

    private fun jdnFromGregorian(y: Int, m: Int, d: Int): Long {
        val a = (14 - m) / 12
        val yy = y + 4800 - a
        val mm = m + 12 * a - 3
        return d + (153 * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045
    }

    fun fromGregorian(y: Int, m: Int, d: Int): EthDate {
        val jdn = jdnFromGregorian(y, m, d)
        val r = Math.floorMod(jdn - EPOCH, 1461L)
        val n = r % 365 + 365 * (r / 1460)
        val year = 4 * Math.floorDiv(jdn - EPOCH, 1461L) + r / 365 - r / 1460
        return EthDate(year.toInt(), (n / 30 + 1).toInt(), (n % 30 + 1).toInt())
    }

    fun of(t: Long): EthDate {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun format(t: Long, withYear: Boolean = true): String {
        val e = of(t)
        return "${months[e.month - 1]} ${e.day}" + if (withYear) ", ${e.year}" else ""
    }

    fun monthLabel(t: Long): String { val e = of(t); return "${months[e.month - 1]} ${e.year}" }
}
