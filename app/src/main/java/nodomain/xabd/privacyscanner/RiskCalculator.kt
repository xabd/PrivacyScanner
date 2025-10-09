package nodomain.xabd.privacyscanner

import android.content.Context
import android.content.pm.PackageManager

object RiskCalculator {

    private val trustedStores = mapOf(
        "org.fdroid.fdroid" to "F-Droid",
        "com.android.vending" to "Google Play",
        "com.aurora.store" to "Aurora Store",
        "com.izzyondroid.installer" to "IzzyOnDroid"
    )

    private val trustedApps = mapOf(
        "org.schabi.newpipe" to "F-Droid (Verified)",
        "com.aurora.services" to "Aurora Services",
        "com.fsck.k9" to "K-9 Mail"
    )

    private val high = listOf(
        "READ_SMS", "SEND_SMS", "RECEIVE_SMS",
        "READ_CONTACTS", "WRITE_CONTACTS",
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
        "RECORD_AUDIO", "RECORD_VIDEO", "CALL_PHONE"
    )

    private val medium = listOf(
        "CAMERA", "READ_EXTERNAL_STORAGE",
        "WRITE_EXTERNAL_STORAGE", "READ_PHONE_STATE"
    )

    /**
     * Calculate risk for an app.
     * @param permissions list of declared permissions (fallback if grantedMap not available)
     * @param grantedMap optional map of permission -> granted state (true/false)
     */
    fun calculate(
        context: Context,
        pkgName: String,
        permissions: List<String>,
        grantedMap: Map<String, Boolean>? = null
    ): Pair<String, String> {

        val prefs = context.getSharedPreferences("trusted_apps", Context.MODE_PRIVATE)

        // ✅ Filter: only include granted permissions if available
        val effectivePermissions = grantedMap?.filterValues { it }?.keys?.toList() ?: permissions

        // 🧩 Step 1: Handle trust overrides
        if (prefs.getBoolean(pkgName, false)) {
            val risk = scoreRisk(effectivePermissions)
            return risk to "User Marked Trusted"
        }

        // 🧩 Step 2: App itself is a known trusted store
        if (trustedStores.containsKey(pkgName)) {
            val risk = scoreRisk(effectivePermissions)
            return risk to "Trusted App Store (${trustedStores[pkgName]})"
        }

        // 🧩 Step 3: Installed via trusted store
        val installer = context.packageManager.getInstallerPackageName(pkgName)
        if (installer != null && trustedStores.containsKey(installer)) {
            val risk = scoreRisk(effectivePermissions)
            return risk to "Trusted (via ${trustedStores[installer]})"
        }

        // 🧩 Step 4: Sideloaded or other installer
        if (installer.isNullOrEmpty()) {
            if (trustedApps.containsKey(pkgName)) {
                val risk = scoreRisk(effectivePermissions)
                return risk to trustedApps[pkgName]!!
            }
            val risk = scoreRisk(effectivePermissions)
            return risk to "Unknown (Sideloaded)"
        }

        // Default
        val risk = scoreRisk(effectivePermissions)
        return risk to "Unknown"
    }

    /**
     * Compute risk score based ONLY on granted permissions.
     */
    private fun scoreRisk(permissions: List<String>): String {
        var score = 0
        permissions.filterNotNull().forEach { p ->
            when {
                high.any { p.uppercase().contains(it) } -> score += 3
                medium.any { p.uppercase().contains(it) } -> score += 2
            }
        }

        return when {
            score >= 7 -> "High Risk (granted)"
            score in 3..6 -> "Medium Risk (granted)"
            score in 1..2 -> "Low Risk (granted)"
            else -> "Safe (no sensitive permissions allowed)"
        }
    }
}


