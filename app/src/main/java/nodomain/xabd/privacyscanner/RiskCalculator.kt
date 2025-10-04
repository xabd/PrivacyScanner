package nodomain.xabd.privacyscanner

import android.content.Context

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

    fun calculate(context: Context, pkgName: String, permissions: List<String>): Pair<String, String> {
        val prefs = context.getSharedPreferences("trusted_apps", Context.MODE_PRIVATE)

        // Case 1: User marked as trusted
        if (prefs.getBoolean(pkgName, false)) {
            val risk = scoreRisk(permissions)
            return risk to "User Marked"
        }

        // Case 2: App is a trusted store itself
        if (trustedStores.containsKey(pkgName)) {
            val risk = scoreRisk(permissions)
            return risk to "Trusted App Store (${trustedStores[pkgName]})"
        }

        // Case 3: Installed via trusted store
        val installer = context.packageManager.getInstallerPackageName(pkgName)
        if (installer != null && trustedStores.containsKey(installer)) {
            val risk = scoreRisk(permissions)
            return risk to "Trusted (via ${trustedStores[installer]})"
        }

        // Case 4: Installer null (sideloaded)
        if (installer.isNullOrEmpty()) {
            if (trustedApps.containsKey(pkgName)) {
                val risk = scoreRisk(permissions)
                return risk to trustedApps[pkgName]!!
            }
            val risk = scoreRisk(permissions)
            return risk to "Unknown (Sideloaded)"
        }

        // Default: permission-based only
        val risk = scoreRisk(permissions)
        return risk to "Unknown"
    }

    private fun scoreRisk(permissions: List<String>): String {
        var score = 0
        permissions.filterNotNull().forEach { p ->
            when {
                high.any { p.uppercase().contains(it) } -> score += 3
                medium.any { p.uppercase().contains(it) } -> score += 2
            }
        }

        return when {
            score >= 7 -> "High Risk ($score)"
            score in 3..6 -> "Medium Risk ($score)"
            score in 1..2 -> "Low Risk ($score)"
            else -> "Safe"
        }
    }
}


