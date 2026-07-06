package com.konodiary.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.konodiary.app.audio.analysis.AnalysisService
import com.konodiary.app.ui.AppRoot
import com.konodiary.app.ui.theme.KonoDiaryTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Result is ignored: analysis works either way; a denial only means the
    // ongoing progress notification stays hidden. We never re-prompt.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        armAnalysisServiceWhileForeground()
        setContent {
            KonoDiaryTheme {
                AppRoot()
            }
        }
    }

    /**
     * The controller's own service start can be DENIED when the enqueue races
     * ahead of the process reaching a foreground state (cold start). While this
     * activity is STARTED an FGS start is always allowed, so re-arm the service
     * whenever the analysis queue is (or becomes) non-empty.
     */
    private fun armAnalysisServiceWhileForeground() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as KonoApp).container.analysisController.progress
                    .map { it.isNotEmpty() }
                    .distinctUntilChanged()
                    .collect { hasWork -> if (hasWork) AnalysisService.start(this@MainActivity) }
            }
        }
    }

    /** Ask once (API 33+) so the analysis service's progress notification can show. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
