package com.spacecay.expenselog.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.spacecay.expenselog.data.Graph

fun biometricAvailable(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun showBiometric(activity: FragmentActivity, onOk: () -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Expense Log")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
    )
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val bio = Graph.prefs.biometric && biometricAvailable(activity)
    LaunchedEffect(Unit) { if (bio) showBiometric(activity, onUnlock) }
    PinPad(
        title = "Enter PIN",
        pin = pin,
        error = if (error) "Wrong PIN" else null,
        onDigit = { d ->
            if (pin.length < 6) {
                pin += d; error = false
                if (pin.length >= 4 && Graph.prefs.checkPin(pin)) onUnlock()
                else if (pin.length == 6) { error = true; pin = "" }
            }
        },
        onBack = { pin = pin.dropLast(1) },
        extra = if (bio) ({ showBiometric(activity, onUnlock) }) else null,
    )
}

@Composable
fun SetPinScreen(onDone: (String?) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    PinPad(
        title = if (first == null) "Choose a 4–6 digit PIN" else "Confirm PIN",
        pin = pin,
        error = error,
        onDigit = { d -> if (pin.length < 6) { pin += d; error = null } },
        onBack = { pin = pin.dropLast(1) },
        okLabel = if (pin.length >= 4) "OK" else null,
        onOk = {
            if (first == null) { first = pin; pin = "" }
            else if (first == pin) onDone(pin)
            else { error = "PINs don't match"; first = null; pin = "" }
        },
        onCancel = { onDone(null) },
    )
}

@Composable
fun PinPad(
    title: String,
    pin: String,
    error: String?,
    onDigit: (String) -> Unit,
    onBack: () -> Unit,
    extra: (() -> Unit)? = null,
    okLabel: String? = null,
    onOk: () -> Unit = {},
    onCancel: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(
                    if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Text(error ?: " ", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("x", "0", "<"))
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(if (k.length == 1 && k[0].isDigit()) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                when (k) {
                                    "<" -> onBack()
                                    "x" -> when { okLabel != null -> onOk(); extra != null -> extra(); onCancel != null -> onCancel() }
                                    else -> onDigit(k)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "<" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Delete")
                            "x" -> when {
                                okLabel != null -> Text(okLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                extra != null -> Icon(Icons.Filled.Fingerprint, "Biometric", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                onCancel != null -> Text("Cancel", style = MaterialTheme.typography.labelLarge)
                            }
                            else -> Text(k, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}
