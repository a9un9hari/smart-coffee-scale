package com.agung.smartgrinder

import android.content.Context
import com.agung.smartgrinder.ble.WeightSource

private const val PREFS_NAME = "smart_grinder_prefs"
private const val KEY_DARK_THEME = "dark_theme"
private const val KEY_LAST_DEVICE_ADDRESS = "last_device_address"
private const val KEY_SMOOTHING_ALPHA = "smoothing_alpha"
private const val KEY_WEIGHT_SOURCE = "weight_source"
private const val KEY_TIMEMORE_AUTOCONNECT = "timemore_autoconnect"

const val DEFAULT_SMOOTHING_ALPHA = 0.35f // mirrors firmware's WEIGHT_SMOOTHING_ALPHA default

/** Settings that need to survive app restarts: theme choice and the last BLE device connected to. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    var lastDeviceAddress: String?
        get() = prefs.getString(KEY_LAST_DEVICE_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_LAST_DEVICE_ADDRESS, value).apply()

    // Firmware doesn't persist this (see BLE_OP_SET_SMOOTHING_ALPHA) - the
    // app is the source of truth and resends it after every connect.
    var smoothingAlpha: Float
        get() = prefs.getFloat(KEY_SMOOTHING_ALPHA, DEFAULT_SMOOTHING_ALPHA)
        set(value) = prefs.edit().putFloat(KEY_SMOOTHING_ALPHA, value).apply()

    // Same reasoning as smoothingAlpha above - firmware keeps this runtime
    // only (see BLE_OP_SET_WEIGHT_SOURCE), the app remembers the user's
    // choice and resends it after every connect.
    var weightSource: WeightSource
        get() = WeightSource.fromWire(prefs.getInt(KEY_WEIGHT_SOURCE, WeightSource.HX711.wireValue))
        set(value) = prefs.edit().putInt(KEY_WEIGHT_SOURCE, value.wireValue).apply()

    // Default true = auto-connect, matching the firmware's own boot default.
    // Same runtime-only pattern as weightSource/smoothingAlpha above.
    var timemoreAutoConnect: Boolean
        get() = prefs.getBoolean(KEY_TIMEMORE_AUTOCONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_TIMEMORE_AUTOCONNECT, value).apply()
}
