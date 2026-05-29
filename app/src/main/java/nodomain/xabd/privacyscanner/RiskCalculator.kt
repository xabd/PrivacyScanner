package nodomain.xabd.privacyscanner

import android.content.Context
import android.os.Build

object RiskCalculator {

    // 🟩 Trusted app stores
    private val trustedStores = mapOf(
        "org.fdroid.fdroid" to "F-Droid",
        "com.android.vending" to "Google Play",
        "com.aurora.store" to "Aurora Store",
        "com.izzyondroid.installer" to "IzzyOnDroid",
        "com.looker.droidify" to "Droid-ify",
        "com.machiav3lli.fdroid" to "Neo Store",
    )

    private val otherKnownInstallers = mapOf(
        "dev.imranr.obtainium" to "Obtainium",
        "com.google.android.packageinstaller" to "Default Installer (Google)",
    )

    // 🟩 Trusted apps
    private val trustedApps = mapOf(
        "org.schabi.newpipe" to "F-Droid (Verified)",
        "com.aurora.services" to "Aurora Services",
        "com.fsck.k9" to "K-9 Mail"
    )

    // 🟥 Critical (privacy-sensitive) permissions
    private val criticalPerms = listOf(
        "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "READ_CONTACTS", "WRITE_CONTACTS",
        "RECORD_AUDIO", "RECORD_VIDEO", "CALL_PHONE", "READ_CALL_LOG", "WRITE_CALL_LOG",
        "READ_CALENDAR", "WRITE_CALENDAR", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
    )

    // 🟧 Medium-risk permissions
    private val mediumPerms = listOf(
        "CAMERA", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "QUERY_ALL_PACKAGES",
        "READ_PHONE_STATE", "BODY_SENSORS", "ACCESS_WIFI_STATE", "ACCESS_NETWORK_STATE"
    )

    // 🟩 Low-risk / normal permissions
    private val lowPerms = listOf(
        "INTERNET", "VIBRATE", "FOREGROUND_SERVICE", "BLUETOOTH", "NFC"
    )

    /**
     * 🧩 Main Risk Calculation Function
     * Returns Pair<RiskLevel, Source + Reason>
     */
    fun calculate(
        context: Context,
        pkgName: String,
        permissions: List<String>,
        grantedMap: Map<String, Boolean>? = null
    ): Pair<String, String> {

        val prefs = context.getSharedPreferences("trusted_apps", Context.MODE_PRIVATE)
        val pm = context.packageManager
        val effectivePermissions = grantedMap?.filterValues { it }?.keys?.toList() ?: permissions

        // ✅ 1. User-marked trusted
        if (prefs.getBoolean(pkgName, false)) {
            return "Safe (User Trusted)" to "Marked trusted by user"
        }

        // ✅ 2. Known trusted apps/stores
        if (trustedStores.containsKey(pkgName)) {
            return "Safe (Trusted Store)" to "Trusted App Store (${trustedStores[pkgName]})"
        }
        if (trustedApps.containsKey(pkgName)) {
            return "Safe (Verified)" to trustedApps[pkgName]!!
        }

        // ✅ 3. Determine installer source
        val installer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkgName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkgName)
            }
        } catch (_: Exception) {
            null
        }

        val (risk, reason) = scoreRisk(effectivePermissions)

        // 🧠 Source explanation
        val source = when {
            installer == null -> "Unknown (Sideloaded)"
            trustedStores.containsKey(installer) -> "Trusted (via ${trustedStores[installer]})"
            otherKnownInstallers.containsKey(installer) -> "Known (via ${otherKnownInstallers[installer]})"
            installer.contains("samsung", true) -> "Trusted (Samsung Store)"
            installer.contains("huawei", true) -> "Trusted (Huawei AppGallery)"
            else -> "Unverified Source ($installer)"
        }

        return risk to "$source • $reason"
    }

    /**
     * 🧩 Weighted scoring with corrected “Safe” classification.
     */
    private fun scoreRisk(permissions: List<String>): Pair<String, String> {
        if (permissions.isEmpty()) {
            return "Safe (no permissions)" to "No permissions requested"
        }

        var score = 0.0
        var hasCritical = false
        var hasMedium = false
        val analyzed = permissions.map { it.uppercase() }

        val foundCritical = mutableListOf<String>()
        val foundMedium = mutableListOf<String>()

        analyzed.forEach { p ->
            when {
                criticalPerms.any { p.contains(it) } -> {
                    score += 12.5
                    hasCritical = true
                    foundCritical.add(p)
                }
                mediumPerms.any { p.contains(it) } -> {
                    score += 6.5
                    hasMedium = true
                    foundMedium.add(p)
                }
                lowPerms.any { p.contains(it) } -> score += 1.0
            }
        }

        // 🧩 Combo risk boost (e.g. Internet + Camera/Mic/Location)
        val hasInternet = analyzed.any { it.contains("INTERNET") }
        val hasCamera = analyzed.any { it.contains("CAMERA") }
        val hasMic = analyzed.any { it.contains("RECORD_AUDIO") }
        val hasLocation = analyzed.any { it.contains("LOCATION") }

        if (hasInternet && (hasCamera || hasMic || hasLocation)) {
            score += 10
        }

        val finalScore = score.coerceIn(0.0, 100.0)

        // 🟩 FIX: Correct “Safe” detection logic
        val risk = when {
            hasCritical -> "High Risk (granted)"
            hasMedium -> "Medium Risk (granted)"
            finalScore in 10.0..29.9 -> "Low Risk (granted)"
            else -> "Safe (no sensitive permissions)"
        }

        // 🧾 Detailed reason
        val reason = when {
            hasCritical -> "Accesses sensitive user data or sensors (${foundCritical.take(3).joinToString(", ")})"
            hasMedium -> "Uses camera, storage, or phone state (${foundMedium.take(3).joinToString(", ")})"
            hasInternet -> "Internet access only (no sensitive data)"
            else -> "No privacy-related permissions detected"
        }

        return risk to reason
    }
}
