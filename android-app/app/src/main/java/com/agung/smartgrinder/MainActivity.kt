package com.agung.smartgrinder

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.agung.smartgrinder.ui.MainScreen
import com.agung.smartgrinder.ui.theme.SmartGrinderTheme

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
            SmartGrinderTheme(darkTheme = darkTheme) {
                MainScreen(
                    viewModel = viewModel,
                    permissionsGranted = permissionsGranted.value,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions) }
                )
            }
        }
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
