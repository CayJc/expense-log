package com.spacecay.expenselog.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Values to pre-fill the add-transaction screen (from screenshots, shared text, templates). */
data class Prefill(
    val type: Int,
    val amount: Long,
    val walletId: Long? = null,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val payee: String = "",
    val dateTime: Long? = null,
    val fee: Long = 0,
    val ref: String? = null,
    val source: Int = 0,
    val photoUri: String? = null,
)

object UiBus {
    val prefill = MutableStateFlow<Prefill?>(null)
    /** Route requests coming from outside compose (notifications, widget, share intents). */
    val pendingRoute = MutableStateFlow<String?>(null)
    fun navigate(route: String) { pendingRoute.value = route }
    val toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
}
