package com.spacecay.expenselog

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.spacecay.expenselog.autolog.AutoLogger
import com.spacecay.expenselog.autolog.ScreenshotScanner
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.ui.AppNav
import com.spacecay.expenselog.ui.UiBus
import com.spacecay.expenselog.ui.screens.LockScreen
import com.spacecay.expenselog.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_ROUTE = "route"
    }

    private var locked by mutableStateOf(false)
    private var lastStop = 0L

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(this)
        locked = Graph.prefs.lockEnabled

        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            val theme by Graph.prefs.theme.collectAsStateWithLifecycle()
            val dark = when (theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
                )
            }
            AppTheme(theme) {
                if (locked) {
                    LockScreen(onUnlock = { locked = false })
                } else {
                    val nav = rememberNavController()
                    AppNav(nav)
                }
            }
        }

        lifecycleScope.launch {
            UiBus.toast.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after 60 s in the background
        if (Graph.prefs.lockEnabled && lastStop > 0 && System.currentTimeMillis() - lastStop > 60_000) locked = true
        lifecycleScope.launch {
            try {
                Graph.repo.ensureSeeded()
                Graph.repo.runDueRecurring()
                val r = ScreenshotScanner.scan(applicationContext)
                if (r.logged > 0) UiBus.toast.tryEmit("Logged ${r.logged} payment${if (r.logged > 1) "s" else ""} from screenshots")
            } catch (_: Exception) {
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastStop = System.currentTimeMillis()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_ROUTE)?.let { route ->
            intent.removeExtra(EXTRA_ROUTE)
            UiBus.navigate(route)
            return
        }
        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type.orEmpty()
            lifecycleScope.launch {
                try {
                    Graph.repo.ensureSeeded()
                    if (type.startsWith("image/")) {
                        @Suppress("DEPRECATION")
                        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
                        if (uri != null) {
                            UiBus.toast.tryEmit("Reading receipt…")
                            UiBus.prefill.value = AutoLogger.prefillFromImage(applicationContext, uri)
                        }
                    } else {
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                        UiBus.prefill.value = AutoLogger.prefillFromText(text)
                    }
                    val p = UiBus.prefill.value
                    UiBus.navigate("txedit?id=0&type=${p?.type ?: 0}")
                } catch (e: Exception) {
                    UiBus.toast.tryEmit("Couldn't read that: ${e.message}")
                }
            }
            intent.action = null
        }
    }
}
