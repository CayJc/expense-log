@file:OptIn(ExperimentalMaterial3Api::class)

package com.spacecay.expenselog.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacecay.expenselog.cloud.Cloud
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.ui.UiBus
import com.spacecay.expenselog.ui.components.AppCard
import com.spacecay.expenselog.ui.components.SettingRow
import com.spacecay.expenselog.ui.components.SimpleTopBar
import com.spacecay.expenselog.util.Dates
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by Cloud.user.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var cloud by remember { mutableStateOf<Cloud.CloudInfo?>(null) }
    var checked by remember { mutableStateOf(false) }
    var firstSyncChoice by remember { mutableStateOf<Cloud.CloudInfo?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf(Graph.prefs.lastCloudSync) }

    fun toast(s: String) { UiBus.toast.tryEmit(s) }
    fun friendly(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            "password is invalid" in m || "INVALID_LOGIN_CREDENTIALS" in m || "credential is incorrect" in m -> "Wrong email or password"
            "no user record" in m -> "No account with that email — tap Create account"
            "already in use" in m -> "That email already has an account — sign in instead"
            "badly formatted" in m -> "That email doesn't look right"
            "at least 6" in m || "WEAK_PASSWORD" in m -> "Password needs at least 6 characters"
            "network" in m.lowercase() -> "No internet connection"
            "cancel" in m.lowercase() -> "Sign-in cancelled"
            else -> m.ifBlank { "Something went wrong" }
        }
    }

    /** After signing in: restore cloud data, or upload this phone's data. */
    suspend fun afterSignIn() {
        val info = try { Cloud.cloudInfo() } catch (e: Exception) { null }
        if (info == null) {
            try { Cloud.upload(); lastSync = Graph.prefs.lastCloudSync; toast("Signed in — your data is backed up") } catch (e: Exception) { error = friendly(e) }
        } else firstSyncChoice = info
    }

    LaunchedEffect(user) {
        if (user != null && !checked) {
            checked = true
            cloud = try { Cloud.cloudInfo() } catch (e: Exception) { null }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { SimpleTopBar("Account", back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val u = user
            if (u == null) {
                Text("Sign in to keep your data safe online and get it back on any phone.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        error = null
                        if (!Cloud.googleAvailable(ctx)) { error = "Google sign-in isn't switched on in Firebase yet — use email for now"; return@Button }
                        busy = true
                        scope.launch {
                            try { Cloud.signInWithGoogle(ctx as Activity); afterSignIn() } catch (e: Exception) { error = friendly(e) }
                            busy = false
                        }
                    },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp)); Text("Continue with Google")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f)); Text("  or email  ", color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(Modifier.weight(1f))
                }
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Email, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        error = null; busy = true
                        scope.launch { try { Cloud.signInEmail(email, password); afterSignIn() } catch (e: Exception) { error = friendly(e) }; busy = false }
                    }, enabled = !busy && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Sign in") }
                    OutlinedButton(onClick = {
                        error = null; busy = true
                        scope.launch { try { Cloud.register(email, password); afterSignIn() } catch (e: Exception) { error = friendly(e) }; busy = false }
                    }, enabled = !busy && email.isNotBlank() && password.length >= 6, modifier = Modifier.weight(1f)) { Text("Create account") }
                }
                TextButton(onClick = {
                    if (email.isBlank()) { error = "Type your email first"; return@TextButton }
                    scope.launch { try { Cloud.resetPassword(email); toast("Reset link sent to $email") } catch (e: Exception) { error = friendly(e) } }
                }) { Text("Forgot password?") }
            } else {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Text((u.displayName ?: u.email ?: "?").take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(u.displayName ?: "Signed in", style = MaterialTheme.typography.titleMedium)
                            Text(u.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                AppCard(padding = PaddingValues(4.dp)) {
                    Column {
                        SettingRow(Icons.Filled.CloudSync, "Back up now",
                            if (lastSync > 0) "Last backed up ${Dates.dateTime(lastSync)} · updates automatically" else "Not backed up yet",
                            onClick = {
                                busy = true
                                scope.launch {
                                    try { Cloud.upload(); lastSync = Graph.prefs.lastCloudSync; cloud = Cloud.cloudInfo(); toast("Backed up") } catch (e: Exception) { error = friendly(e) }
                                    busy = false
                                }
                            })
                        SettingRow(Icons.Filled.CloudDownload, "Restore from cloud",
                            cloud?.let { "Cloud copy from ${Dates.dateTime(it.updatedAt)} · ${it.transactions} transactions · ${it.device}" } ?: "No cloud copy yet",
                            onClick = { if (cloud != null) confirmRestore = true })
                        SettingRow(Icons.AutoMirrored.Filled.Logout, "Sign out", "Data stays on this phone", onClick = { Cloud.signOut(); checked = false; cloud = null })
                    }
                }
                Text("Your data is stored in your own Firebase project and only your account can read it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) Text("Working…", color = MaterialTheme.colorScheme.primary)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    firstSyncChoice?.let { info ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Your cloud data was found") },
            text = { Text("Cloud copy from ${Dates.dateTime(info.updatedAt)} (${info.transactions} transactions, ${info.device}).\n\nUse it on this phone, or replace it with this phone's data?") },
            confirmButton = {
                TextButton(onClick = {
                    firstSyncChoice = null; busy = true
                    scope.launch { try { Cloud.download(); lastSync = Graph.prefs.lastCloudSync; toast("Restored from cloud") } catch (e: Exception) { error = friendly(e) }; busy = false }
                }) { Text("Use cloud data") }
            },
            dismissButton = {
                TextButton(onClick = {
                    firstSyncChoice = null; busy = true
                    scope.launch { try { Cloud.upload(); lastSync = Graph.prefs.lastCloudSync; toast("Uploaded this phone's data") } catch (e: Exception) { error = friendly(e) }; busy = false }
                }) { Text("Keep this phone's") }
            },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from cloud?") },
            text = { Text("This phone's data is replaced by the cloud copy.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false; busy = true
                    scope.launch { try { Cloud.download(); lastSync = Graph.prefs.lastCloudSync; toast("Restored") } catch (e: Exception) { error = friendly(e) }; busy = false }
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}
