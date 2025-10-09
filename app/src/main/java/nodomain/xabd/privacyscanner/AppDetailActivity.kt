package nodomain.xabd.privacyscanner

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class AppDetailActivity : BaseActivity() {

    private lateinit var tvRisk: TextView
    private lateinit var tvSource: TextView
    private lateinit var btnTrust: Button
    private lateinit var pkgName: String
    private lateinit var appLabel: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_detail)

        val ivAppIcon = findViewById<ImageView>(R.id.ivAppIcon)
        val tvAppName = findViewById<TextView>(R.id.tvAppName)
        val tvPackageName = findViewById<TextView>(R.id.tvPackageName)
        tvRisk = findViewById(R.id.tvRisk)
        tvSource = findViewById(R.id.tvSource)
        val tvPermissions = findViewById<TextView>(R.id.tvPermissions)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        btnTrust = findViewById(R.id.btnTrust)

        pkgName = intent.getStringExtra("PACKAGE_NAME") ?: ""
        if (pkgName.isBlank()) {
            Toast.makeText(this, "No package provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val pm = packageManager
        appLabel = pkgName
        try {
            val ai = pm.getApplicationInfo(pkgName, 0)
            appLabel = pm.getApplicationLabel(ai).toString()
            val icon = pm.getApplicationIcon(pkgName)
            ivAppIcon.setImageDrawable(icon)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w("AppDetailActivity", "Package not found: $pkgName", e)
        }

        // 🔥 Get *only granted* permissions
        val grantedPermissions = mutableListOf<String>()
        val grantedMap = mutableMapOf<String, Boolean>()
        try {
            val packageInfo: PackageInfo = pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS)
            val requested = packageInfo.requestedPermissions
            val flags = packageInfo.requestedPermissionsFlags
            if (requested != null && flags != null) {
                for (i in requested.indices) {
                    val granted = (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    grantedMap[requested[i]] = granted
                    if (granted) grantedPermissions.add(requested[i])
                }
            }
        } catch (e: Exception) {
            Log.e("AppDetailActivity", "Failed to load permissions for $pkgName", e)
        }

        // 🧮 Calculate risk based only on *granted* permissions
        val (risk, source) = RiskCalculator.calculate(this, pkgName, grantedPermissions)

        // 🖥️ UI setup
        tvAppName.text = appLabel
        tvPackageName.text = pkgName
        tvRisk.text = risk
        tvSource.text = "Source: $source"
        applyRiskColor(risk)
        updateTrustButton()

        // 🧩 Build permissions list with allowed/denied display
        try {
            val sb = SpannableStringBuilder()
            val highRiskKeywords = listOf(
                "READ_SMS", "SEND_SMS", "RECEIVE_SMS",
                "READ_CONTACTS", "WRITE_CONTACTS",
                "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
                "RECORD_AUDIO", "RECORD_VIDEO", "CALL_PHONE"
            )

            if (grantedMap.isEmpty()) {
                tvPermissions.text = "No permissions found."
            } else {
                grantedMap.forEach { (perm, granted) ->
                    val start = sb.length
                    sb.append("• $perm ")
                    val statusText = if (granted) "(Allowed)" else "(Not allowed)"
                    val statusColor = if (granted)
                        Color.parseColor("#388E3C")
                    else
                        Color.parseColor("#D32F2F")
                    val statusStart = sb.length
                    sb.append(statusText)
                    val statusEnd = sb.length
                    sb.setSpan(
                        android.text.style.ForegroundColorSpan(statusColor),
                        statusStart,
                        statusEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.append("\n")

                    // Highlight high-risk permissions (even if not granted)
                    val isHigh = highRiskKeywords.any { kw -> perm.uppercase().contains(kw) }
                    if (isHigh) {
                        val highlightColor =
                            ContextCompat.getColor(this, R.color.permissionHighlight)
                        sb.setSpan(
                            BackgroundColorSpan(highlightColor),
                            start,
                            statusEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                tvPermissions.setText(sb, TextView.BufferType.SPANNABLE)
            }
        } catch (e: Exception) {
            Log.e("AppDetailActivity", "Error building permissions list", e)
            tvPermissions.text = "Unable to load permissions."
        }

        // ⚙️ Open app settings
        btnSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkgName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("AppDetailActivity", "Failed to open app settings", e)
                Toast.makeText(this, "Unable to open app settings", Toast.LENGTH_SHORT).show()
            }
        }

        // 🤝 Trust/Untrust toggle
        btnTrust.setOnClickListener {
            val prefs = getSharedPreferences("trusted_apps", MODE_PRIVATE)
            val trusted = prefs.getBoolean(pkgName, false)
            if (trusted) {
                prefs.edit().remove(pkgName).apply()
                Toast.makeText(this, "$appLabel removed from Trusted", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putBoolean(pkgName, true).apply()
                Toast.makeText(this, "$appLabel marked as Trusted", Toast.LENGTH_SHORT).show()
            }
            val (newRisk, newSource) = RiskCalculator.calculate(this, pkgName, grantedPermissions)
            tvRisk.text = newRisk
            tvSource.text = "Source: $newSource"
            applyRiskColor(newRisk)
            updateTrustButton()
        }
    }

    private fun updateTrustButton() {
        val prefs = getSharedPreferences("trusted_apps", MODE_PRIVATE)
        val trusted = prefs.getBoolean(pkgName, false)
        btnTrust.text = if (trusted) "Untrust This App" else "Trust This App"
    }

    private fun applyRiskColor(risk: String) {
        when {
            risk.contains("Trusted App Store") -> tvRisk.setTextColor(Color.parseColor("#1976D2"))
            risk.contains("Trusted") -> tvRisk.setTextColor(Color.parseColor("#00796B"))
            risk.contains("High") -> tvRisk.setTextColor(Color.parseColor("#D32F2F"))
            risk.contains("Medium") -> tvRisk.setTextColor(Color.parseColor("#F57C00"))
            risk.contains("Low") -> tvRisk.setTextColor(Color.parseColor("#FBC02D"))
            else -> tvRisk.setTextColor(Color.parseColor("#388E3C"))
        }
        tvRisk.setTypeface(tvRisk.typeface, Typeface.BOLD)
    }
}



