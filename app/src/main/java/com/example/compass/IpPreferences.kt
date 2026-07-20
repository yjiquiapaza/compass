package com.example.compass;

import android.content.Context


object IpPreferences {
    private const val PREFS_NAME = "brujula_prefs"
    private const val KEY_IP = "target_ip"

    fun getSavedIp(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_IP, "") ?: ""
    }

    fun saveIp(context: Context, ip: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IP, ip).apply()
    }
}

fun isIpValid(ip: String): Boolean{
    val items = ip.trim().split(".")
    if (items.size != 4) return false
    return items.all { item ->
        val n = item.toIntOrNull()
        n != null && n in 0..255
    }
}
