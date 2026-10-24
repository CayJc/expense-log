package com.spacecay.expenselog.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Time range [start, end). */
data class Range(val start: Long, val end: Long) {
    operator fun contains(t: Long) = t in start until end
}

enum class PeriodKind { DAY, WEEK, MONTH, YEAR, ALL }

object Dates {
    var firstDayOfWeek = Calendar.MONDAY
    var monthStartDay = 1

    fun cal(t: Long = System.currentTimeMillis()): Calendar =
        Calendar.getInstance().apply { timeInMillis = t; firstDayOfWeek = Dates.firstDayOfWeek }

    fun startOfDay(t: Long): Long = cal(t).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun day(t: Long): Range {
        val s = startOfDay(t)
        val e = cal(s).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        return Range(s, e)
    }

    fun week(t: Long): Range {
        val c = cal(startOfDay(t))
        while (c.get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) c.add(Calendar.DAY_OF_MONTH, -1)
        val s = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 7)
        return Range(s, c.timeInMillis)
    }

    /** Month containing t, honouring a custom start day (e.g. salary on the 25th). */
    fun month(t: Long): Range {
        val c = cal(startOfDay(t))
        val sd = monthStartDay.coerceIn(1, 28)
        if (c.get(Calendar.DAY_OF_MONTH) < sd) c.add(Calendar.MONTH, -1)
        c.set(Calendar.DAY_OF_MONTH, sd)
        val s = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return Range(s, c.timeInMillis)
    }

    fun calendarMonth(t: Long): Range {
        val c = cal(startOfDay(t))
        c.set(Calendar.DAY_OF_MONTH, 1)
        val s = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return Range(s, c.timeInMillis)
    }

    fun year(t: Long): Range {
        val c = cal(startOfDay(t))
        c.set(Calendar.DAY_OF_YEAR, 1)
        val s = c.timeInMillis
        c.add(Calendar.YEAR, 1)
        return Range(s, c.timeInMillis)
    }

    fun range(kind: PeriodKind, t: Long): Range = when (kind) {
        PeriodKind.DAY -> day(t)
        PeriodKind.WEEK -> week(t)
        PeriodKind.MONTH -> month(t)
        PeriodKind.YEAR -> year(t)
        PeriodKind.ALL -> Range(0L, 4102444800000L)
    }

    fun shift(kind: PeriodKind, t: Long, by: Int): Long {
        val c = cal(t)
        when (kind) {
            PeriodKind.DAY -> c.add(Calendar.DAY_OF_MONTH, by)
            PeriodKind.WEEK -> c.add(Calendar.WEEK_OF_YEAR, by)
            PeriodKind.MONTH -> c.add(Calendar.MONTH, by)
            PeriodKind.YEAR -> c.add(Calendar.YEAR, by)
            PeriodKind.ALL -> {}
        }
        return c.timeInMillis
    }

    fun label(kind: PeriodKind, t: Long): String {
        val r = range(kind, t)
        return when (kind) {
            PeriodKind.DAY -> fmt("EEE, d MMM yyyy", t)
            PeriodKind.WEEK -> "${fmt("d MMM", r.start)} – ${fmt("d MMM yyyy", r.end - 1)}"
            PeriodKind.MONTH -> if (monthStartDay == 1) fmt("MMMM yyyy", t)
                else "${fmt("d MMM", r.start)} – ${fmt("d MMM yyyy", r.end - 1)}"
            PeriodKind.YEAR -> fmt("yyyy", t)
            PeriodKind.ALL -> "All time"
        }
    }

    fun fmt(pattern: String, t: Long): String = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(t))

    fun dayHeader(t: Long): String {
        val today = startOfDay(System.currentTimeMillis())
        val d = startOfDay(t)
        return when (d) {
            today -> "Today"
            cal(today).apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis -> "Yesterday"
            else -> fmt("EEEE, d MMMM", t)
        }
    }

    fun time(t: Long) = fmt("HH:mm", t)
    fun date(t: Long) = fmt("d MMM yyyy", t)
    fun dateTime(t: Long) = fmt("d MMM yyyy, HH:mm", t)

    /** Combine the date part of [date] with the time-of-day of [time]. */
    fun combine(date: Long, hour: Int, minute: Int): Long = cal(date).apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** DatePicker returns UTC midnight; convert to local midnight of the same calendar date. */
    fun fromPickerUtc(utcMillis: Long): Long {
        val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    /** Local date -> UTC midnight for DatePicker initial value. */
    fun toPickerUtc(local: Long): Long {
        val c = cal(local)
        return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    fun daysBetween(a: Long, b: Long): Int = ((startOfDay(b) - startOfDay(a)) / 86_400_000L).toInt()

    fun relative(t: Long): String {
        val d = daysBetween(t, System.currentTimeMillis())
        return when {
            d <= 0 -> "today"
            d == 1 -> "yesterday"
            d < 30 -> "$d days ago"
            d < 365 -> "${d / 30} months ago"
            else -> "${d / 365} years ago"
        }
    }
}
