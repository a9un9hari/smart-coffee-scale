package com.agung.smartgrinder

import android.content.Context

private const val PREFS_NAME = "smart_grinder_prefs"
private const val KEY_DARK_THEME = "dark_theme"
private const val KEY_LAST_DEVICE_ADDRESS = "last_device_address"

/** Settings that need to survive app restarts: theme choice and the last BLE device connected to. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    var lastDeviceAddress: String?
        get() = prefs.getString(KEY_LAST_DEVICE_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_LAST_DEVICE_ADDRESS, value).apply()
}
