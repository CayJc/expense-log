package com.spacecay.expenselog

import android.app.Application
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.work.Notifier
import com.spacecay.expenselog.work.Scheduler
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        com.spacecay.expenselog.cloud.Cloud.init()
        Notifier.channels(this)
        Graph.scope.launch {
            Graph.repo.ensureSeeded()
            Graph.repo.runDueRecurring()
        }
        Scheduler.scheduleAll(this)
    }
}
