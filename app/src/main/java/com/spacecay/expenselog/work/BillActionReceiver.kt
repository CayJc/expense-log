package com.spacecay.expenselog.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.spacecay.expenselog.data.Graph
import kotlinx.coroutines.launch

/** Handles "Add it" / "Skip" on bill reminder notifications. */
class BillActionReceiver : BroadcastReceiver() {
    companion object {
        const val ADD = "com.spacecay.expenselog.BILL_ADD"
        const val SKIP = "com.spacecay.expenselog.BILL_SKIP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Graph.init(context)
        val rid = intent.getLongExtra("rid", 0L)
        val due = intent.getLongExtra("due", 0L)
        val pending = goAsync()
        Graph.scope.launch {
            try {
                val r = Graph.dao.getRecurring(rid)
                // Only act if the bill is still at the date the notification was about.
                if (r != null && r.nextDate == due) {
                    if (intent.action == ADD) Graph.repo.payBill(r) else Graph.repo.advanceBill(r)
                }
                Notifier.cancel(context, 5000 + rid.toInt())
            } finally {
                pending.finish()
            }
        }
    }
}
