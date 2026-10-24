package com.spacecay.expenselog.ui

import com.spacecay.expenselog.data.Budget
import com.spacecay.expenselog.data.BudgetPeriod
import com.spacecay.expenselog.data.TxType
import com.spacecay.expenselog.data.Txn
import com.spacecay.expenselog.util.Dates
import com.spacecay.expenselog.util.PeriodKind
import com.spacecay.expenselog.util.Range

object BudgetMath {
    fun ids(s: String): Set<Long> = s.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

    fun kind(b: Budget): PeriodKind? = when (b.period) {
        BudgetPeriod.WEEKLY -> PeriodKind.WEEK
        BudgetPeriod.MONTHLY -> PeriodKind.MONTH
        BudgetPeriod.YEARLY -> PeriodKind.YEAR
        else -> null
    }

    fun range(b: Budget, at: Long = System.currentTimeMillis()): Range {
        val k = kind(b) ?: return Range(Dates.startOfDay(b.startDate), Dates.day(b.endDate ?: b.startDate).end)
        return Dates.range(k, at)
    }

    fun matches(b: Budget, t: Txn, bd: BookData): Boolean {
        if (!Stats.counts(t) || t.type != TxType.EXPENSE) return false
        val cats = ids(b.categoryIds)
        val wallets = ids(b.walletIds)
        if (wallets.isNotEmpty() && t.walletId !in wallets) return false
        if (cats.isEmpty()) return true
        val c = t.categoryId ?: return false
        if (c in cats) return true
        val parent = bd.catMap[c]?.parentId
        return parent != null && parent in cats
    }

    fun spent(b: Budget, txns: List<Txn>, bd: BookData, r: Range = range(b)): Long =
        txns.filter { it.dateTime in r && matches(b, it, bd) }.sumOf { bd.mainAmount(it) + bd.rates.toMain(it.fee, bd.currencyOf(it.walletId)) }

    fun daysLeft(r: Range): Int = maxOf(0, Dates.daysBetween(System.currentTimeMillis(), r.end - 1) + 1)
}
