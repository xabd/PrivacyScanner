package com.example.privacyscanner

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val permissions: List<String>,
    val riskLevel: String,
    val icon: Drawable,
    val isSystemApp: Boolean = false
)
