package com.agung.smartgrinder

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.agung.smartgrinder.ui.MainScreen
import com.agung.smartgrinder.ui.theme.SmartGrinderTheme
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private val viewModel: GrinderViewModel by viewModels()

    private val requiredPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionsGranted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionsGranted.value = hasPermissions()

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsGranted.value = hasPermissions() }

        setContent {
            val darkTheme by viewModel.darkTheme.collectAsState()

            // A short delay before capturing - the state flip to
            // SHOT_COMPLETE and this event fire in the same instant, before
            // Compose has necessarily recomposed to show it yet.
            LaunchedEffect(Unit) {
                viewModel.shotCompletedEvents.collect { timestamp ->
                    delay(400)
                    captureShotScreenshot(timestamp)
                }
            }

            SmartGrinderTheme(darkTheme = darkTheme) {
                MainScreen(
                    viewModel = viewModel,
                    permissionsGranted = permissionsGranted.value,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions) }
                )
            }
        }
    }

    /** Saved as shot_<timestamp>.png alongside shot_logs.jsonl's matching entry (see ShotLogger). */
    private fun captureShotScreenshot(timestamp: Long) {
        val view = window.decorView
        if (view.width == 0 || view.height == 0) return
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(window, bitmap, { result ->
            if (result != PixelCopy.SUCCESS) return@request
            try {
                FileOutputStream(File(getExternalFilesDir(null), "shot_$timestamp.png")).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (e: Exception) {
                // Best-effort - a failed screenshot should never crash the app.
            }
        }, Handler(Looper.getMainLooper()))
    }

    override fun onResume() {
        super.onResume()
        // covers the case where the user granted permissions from system settings
        // rather than the in-app prompt
        permissionsGranted.value = hasPermissions()
    }

    private fun hasPermissions(): Boolean = requiredPermissions.all {
        checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
