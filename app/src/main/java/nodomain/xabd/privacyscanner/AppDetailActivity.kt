package nodomain.xabd.privacyscanner

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.content.edit

class AppDetailActivity : BaseActivity() {

    private lateinit var tvRisk: TextView
    private lateinit var tvSource: TextView
    private lateinit var tvReason: TextView
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
        tvReason = findViewById(R.id.tvReason)
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
            ivAppIcon.setImageDrawable(pm.getApplicationIcon(pkgName))
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w("AppDetailActivity", "Package not found: $pkgName", e)
        }

        // 🔹 Fetch granted permissions
        val grantedPermissions = mutableListOf<String>()
        val grantedMap = mutableMapOf<String, Boolean>()
        try {
            val pkgInfo: PackageInfo = pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS)
            val requested = pkgInfo.requestedPermissions
            val flags = pkgInfo.requestedPermissionsFlags
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

        // 🔹 Calculate risk (includes reason)
        val (risk, sourceReason) = RiskCalculator.calculate(this, pkgName, grantedPermissions)
        val splitInfo = sourceReason.split("•", limit = 2)
        val source = splitInfo.getOrNull(0)?.trim() ?: "Unknown"
        val reason = splitInfo.getOrNull(1)?.trim() ?: "No additional context"

        val riskEmoji = when {
            risk.contains("High", true) -> "🔴"
            risk.contains("Medium", true) -> "🟠"
            risk.contains("Low", true) -> "🟡"
            risk.contains("Safe", true) -> "🟢"
            else -> "⚪"
        }

        tvAppName.text = appLabel
        tvPackageName.text = pkgName
        tvRisk.text = getString(R.string.textview_showing_risk, riskEmoji, risk)
        tvSource.text = getString(R.string.textview_source, source)
        tvReason.text = getString(R.string.textview_reason, reason)

        applyRiskColor(risk, animate = false)
        updateTrustButton()

        // 🔹 Highlight permissions
        val sb = SpannableStringBuilder()
        val highRiskKeywords = listOf(
            "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "READ_CONTACTS",
            "WRITE_CONTACTS", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
            "RECORD_AUDIO", "CALL_PHONE"
        )

        if (grantedMap.isEmpty()) {
            tvPermissions.text = getString(R.string.textview_no_permissions_found)
        } else {
            sb.append("Permissions:\n\n")
            grantedMap.forEach { (perm, granted) ->
                val start = sb.length
                sb.append("• $perm ")
                val statusText = if (granted) "(Allowed)" else "(Not allowed)"
                val statusColor = if (granted)
                    "#4CAF50".toColorInt()
                else
                    "#D32F2F".toColorInt()

                val statusStart = sb.length
                sb.append(statusText)
                val statusEnd = sb.length

                sb.setSpan(ForegroundColorSpan(statusColor), statusStart, statusEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                val isHigh = highRiskKeywords.any { kw -> perm.uppercase().contains(kw) }
                if (isHigh && granted) {
                    val highlightColor = ContextCompat.getColor(this, R.color.permissionHighlight)
                    sb.setSpan(BackgroundColorSpan(highlightColor), start, statusEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, statusEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                sb.append("\n")
            }
            tvPermissions.text = sb
        }

        // 🔹 Buttons
        btnSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$pkgName".toUri()
            }
            startActivity(intent)
        }

        btnTrust.setOnClickListener {
            val prefs = getSharedPreferences("trusted_apps", MODE_PRIVATE)
            val trusted = prefs.getBoolean(pkgName, false)
            if (trusted) {
                prefs.edit { remove(pkgName) }
                Toast.makeText(this, "$appLabel removed from Trusted", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit { putBoolean(pkgName, true) }
                Toast.makeText(this, "$appLabel marked as Trusted", Toast.LENGTH_SHORT).show()
            }

            val (newRisk, newSourceReason) = RiskCalculator.calculate(this, pkgName, grantedPermissions)
            val newSplit = newSourceReason.split("•", limit = 2)
            tvRisk.text = getString(R.string.textview_risk_text, riskEmoji, newRisk)
            tvSource.text =
                getString(R.string.textview_source_text, newSplit.getOrNull(0)?.trim() ?: "Unknown")
            tvReason.text = getString(
                R.string.textview_reason_text,
                newSplit.getOrNull(1)?.trim() ?: "No context"
            )

            applyRiskColor(newRisk, animate = true)
            updateTrustButton()
        }
    }

    private fun updateTrustButton() {
        val prefs = getSharedPreferences("trusted_apps", MODE_PRIVATE)
        val trusted = prefs.getBoolean(pkgName, false)
        btnTrust.text = if (trusted) "Untrust This App" else "Trust This App"
    }

    // 🟩 Guaranteed correct & distinct color mapping + animation
    private fun applyRiskColor(risk: String, animate: Boolean) {
        val label = risk.lowercase()
        val colorText: Int
        val colorCard: Int

        when {
            label.contains("high") -> {
                colorText = "#FF5252".toColorInt() // red
                colorCard = "#33FF5252".toColorInt()
            }
            label.contains("medium") -> {
                colorText = "#FFA000".toColorInt() // orange
                colorCard = "#33FFA000".toColorInt()
            }
            label.contains("low") -> {
                colorText = "#FFEB3B".toColorInt() // yellow
                colorCard = "#33FFEB3B".toColorInt()
            }
            label.contains("safe") -> {
                colorText = "#00C853".toColorInt() // ✅ pure green (brighter)
                colorCard = "#3300C853".toColorInt()
            }
            label.contains("trusted") -> {
                colorText = "#2196F3".toColorInt() // blue
                colorCard = "#332196F3".toColorInt()
            }
            else -> {
                colorText = "#9E9E9E".toColorInt() // gray
                colorCard = "#222222".toColorInt()
            }
        }

        val card = findViewById<LinearLayout>(R.id.riskInfoCard)

        if (animate) {
            val currentTextColor = tvRisk.currentTextColor
            val textAnim = ValueAnimator.ofObject(ArgbEvaluator(), currentTextColor, colorText)
            textAnim.addUpdateListener { animator ->
                tvRisk.setTextColor(animator.animatedValue as Int)
            }

            val currentBg = (card.background as? android.graphics.drawable.ColorDrawable)?.color ?: Color.TRANSPARENT
            val bgAnim = ValueAnimator.ofObject(ArgbEvaluator(), currentBg, colorCard)
            bgAnim.addUpdateListener { animator ->
                card.setBackgroundColor(animator.animatedValue as Int)
            }

            textAnim.duration = 400
            bgAnim.duration = 400
            textAnim.start()
            bgAnim.start()
        } else {
            tvRisk.setTextColor(colorText)
            card.setBackgroundColor(colorCard)
        }

        tvRisk.setTypeface(tvRisk.typeface, Typeface.BOLD)
    }
}
