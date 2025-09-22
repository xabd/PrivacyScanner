package com.example.privacyscanner

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AppDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_detail)

        val tvDetails = findViewById<TextView>(R.id.tvDetails)
        val btnUninstall = findViewById<Button>(R.id.btnUninstall)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        val packageName = intent.getStringExtra("PACKAGE_NAME")
        val pm = packageManager

        if (packageName != null) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo)

                val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                val permissions = pkgInfo.requestedPermissions?.toList() ?: emptyList()

                val risk = calculateRisk(permissions)

                Log.d("PrivacyScanner", "Loaded $appName ($packageName) with ${permissions.size} permissions")

                val builder = StringBuilder()
                builder.append("App: $appName\n")
                builder.append("Package: $packageName\n")
                builder.append("Risk: $risk\n\n")

                if (permissions.isNotEmpty()) {
                    builder.append("Permissions:\n")
                    permissions.forEach { perm ->
                        builder.append(" • $perm\n")
                    }
                } else {
                    builder.append("No permissions found for this app.")
                }

                tvDetails.text = builder.toString()

                // --- Quick Actions ---
                if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // System app → disable uninstall
                    btnUninstall.isEnabled = false
                    btnUninstall.alpha = 0.5f
                } else {
                    // User app → enable uninstall
                    btnUninstall.isEnabled = true
                    btnUninstall.alpha = 1f
                    btnUninstall.setOnClickListener {
                        try {
                            val uninstallIntent = Intent(Intent.ACTION_DELETE)
                            uninstallIntent.data = Uri.parse("package:$packageName")
                            startActivity(uninstallIntent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Unable to uninstall app.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Open app settings
                btnSettings.setOnClickListener {
                    try {
                        val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        settingsIntent.data = Uri.parse("package:$packageName")
                        startActivity(settingsIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Unable to open settings.", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("PrivacyScanner", "Error: ${e.message}", e)
                tvDetails.text = "Error loading permissions: ${e.message}"
            }
        } else {
            tvDetails.text = "No package name received."
        }
    }

    private fun calculateRisk(permissions: List<String>): String {
        val high = listOf(
            "READ_SMS", "SEND_SMS", "RECEIVE_SMS",
            "READ_CONTACTS", "WRITE_CONTACTS",
            "RECORD_AUDIO", "RECORD_VIDEO",
            "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"
        )
        val medium = listOf(
            "CAMERA", "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE", "READ_PHONE_STATE"
        )

        var score = 0
        permissions.forEach { perm ->
            val p = perm.uppercase()
            when {
                high.any { p.contains(it) } -> score += 3
                medium.any { p.contains(it) } -> score += 2
            }
        }

        return when {
            score >= 6 -> "High Risk"
            score >= 3 -> "Medium Risk"
            score >= 1 -> "Low Risk"
            else -> "Safe"
        }
    }
}







