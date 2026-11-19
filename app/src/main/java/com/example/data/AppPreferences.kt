package com.example.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("ym_balance_prefs_v2", Context.MODE_PRIVATE)

    fun isDarkTheme(): Boolean = prefs.getBoolean(KEY_DARK_THEME, false)

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun isDemoMode(): Boolean = prefs.getBoolean(KEY_DEMO_MODE, false)

    fun setDemoMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
    }

    fun saveToken(phone: String, token: String) {
        prefs.edit().putString("$KEY_TOKEN_PREFIX$phone", token).apply()
    }

    fun getToken(phone: String): String? {
        return prefs.getString("$KEY_TOKEN_PREFIX$phone", null)?.takeIf { it.isNotBlank() }
    }

    fun clearToken(phone: String) {
        prefs.edit().remove("$KEY_TOKEN_PREFIX$phone").apply()
    }

    fun getSabafonVerifyPath(): String {
        val saved = prefs.getString(KEY_SABAFON_VERIFY_PATH, DEFAULT_SABAFON_VERIFY_PATH)
            .orEmpty()
            .trim()
        // Auto-migrate previously known 404 path to ASP.NET Authenticate/Login
        if (saved.isBlank() || saved.equals("/ana-sabafon-api-auth/Authenticate/VerifyOtp", ignoreCase = true)) {
            return DEFAULT_SABAFON_VERIFY_PATH
        }
        return saved
    }

    fun setSabafonVerifyPath(path: String) {
        prefs.edit().putString(KEY_SABAFON_VERIFY_PATH, path.trim()).apply()
    }

    fun getSabafonBalancePath(): String {
        return prefs.getString(
            KEY_SABAFON_BALANCE_PATH,
            DEFAULT_SABAFON_BALANCE_PATH
        ).orEmpty().ifBlank { DEFAULT_SABAFON_BALANCE_PATH }
    }

    fun setSabafonBalancePath(path: String) {
        prefs.edit().putString(KEY_SABAFON_BALANCE_PATH, path.trim()).apply()
    }

    fun getSabafonPackagesPath(): String {
        return prefs.getString(
            KEY_SABAFON_PACKAGES_PATH,
            DEFAULT_SABAFON_PACKAGES_PATH
        ).orEmpty().ifBlank { DEFAULT_SABAFON_PACKAGES_PATH }
    }

    fun setSabafonPackagesPath(path: String) {
        prefs.edit().putString(KEY_SABAFON_PACKAGES_PATH, path.trim()).apply()
    }

    companion object {
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_DEMO_MODE = "demo_mode"
        private const val KEY_TOKEN_PREFIX = "bearer_token_"
        private const val KEY_SABAFON_VERIFY_PATH = "sabafon_verify_path"
        private const val KEY_SABAFON_BALANCE_PATH = "sabafon_balance_path"
        private const val KEY_SABAFON_PACKAGES_PATH = "sabafon_packages_path"

        const val DEFAULT_SABAFON_VERIFY_PATH = "/ana-sabafon-api-auth/Authenticate/Login"
        const val DEFAULT_SABAFON_BALANCE_PATH = "/ana-sabafon-api-subscriber/Subscriber/GetBalance"
        const val DEFAULT_SABAFON_PACKAGES_PATH = "/ana-sabafon-api-subscriber/Subscriber/GetActivePackages"
    }
}
