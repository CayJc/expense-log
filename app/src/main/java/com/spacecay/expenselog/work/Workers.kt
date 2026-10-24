package com.spacecay.expenselog.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spacecay.expenselog.autolog.ScreenshotScanner
import com.spacecay.expenselog.backup.Backup
import com.spacecay.expenselog.data.Graph
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Every ~15 min: recurring transactions, new payment screenshots, auto-backup. */
class PeriodicWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        try { Graph.repo.ensureSeeded() } catch (_: Exception) {}
        try { Graph.repo.runDueRecurring() } catch (_: Exception) {}
        try { Graph.repo.notifyDueBills(applicationContext) } catch (_: Exception) {}
        try { ScreenshotScanner.scan(applicationContext) } catch (_: Exception) {}
        try { Backup.autoBackupIfDue(applicationContext) } catch (_: Exception) {}
        try { com.spacecay.expenselog.cloud.Cloud.init(); com.spacecay.expenselog.cloud.Cloud.syncIfNeeded() } catch (_: Exception) {}
        return Result.success()
    }
}

class DailySummaryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        if (Graph.prefs.dailySummary) {
            try { Notifier.dailySummary(applicationContext) } catch (_: Exception) {}
        }
        Scheduler.scheduleDaily(applicationContext)
        return Result.success()
    }
}

object Scheduler {
    fun scheduleAll(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PeriodicWorker>(15, TimeUnit.MINUTES).build(),
        )
        scheduleDaily(ctx)
    }

    fun scheduleDaily(ctx: Context) {
        val prefs = Graph.prefs
        val wm = WorkManager.getInstance(ctx)
        if (!prefs.dailySummary) { wm.cancelUniqueWork("daily"); return }
        val mins = prefs.dailySummaryMinutes
        val now = Calendar.getInstance()
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, mins / 60); set(Calendar.MINUTE, mins % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val delay = at.timeInMillis - now.timeInMillis
        wm.enqueueUniqueWork(
            "daily", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailySummaryWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
        )
    }

    fun runNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork("now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<PeriodicWorker>().build())
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Graph.init(context)
        Scheduler.scheduleAll(context)
    }
}
