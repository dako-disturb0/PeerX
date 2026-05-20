package com.example.data

import android.content.Context
import android.content.SharedPreferences

class ProfileManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("peerx_profile", Context.MODE_PRIVATE)

    fun register(name: String, hash: String) {
        prefs.edit()
            .putString("name", name)
            .putString("hash", hash)
            .putBoolean("registered", true)
            .apply()
    }

    fun isRegistered(): Boolean = prefs.getBoolean("registered", false)

    fun getName(): String = prefs.getString("name", "") ?: ""
    fun getHash(): String = prefs.getString("hash", "") ?: ""

    fun updateName(name: String) {
        prefs.edit().putString("name", name).apply()
    }

    fun updateHash(hash: String) {
        prefs.edit().putString("hash", hash).apply()
    }

    fun getHideInfo(): Boolean = prefs.getBoolean("hideInfo", false)
    fun setHideInfo(hide: Boolean) {
        prefs.edit().putBoolean("hideInfo", hide).apply()
    }

    fun getTheme(): String = prefs.getString("theme", "dark") ?: "dark"
    fun setTheme(theme: String) {
        prefs.edit().putString("theme", theme).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
