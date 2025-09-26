package nodomain.xabd.privacyscanner

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import nodomain.xabd.privacyscanner.R

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var txtLoading: TextView
    private lateinit var chkSystemApps: CheckBox
    private lateinit var btnScan: Button
    private lateinit var btnWebsite: Button

    private var showSystemApps = false
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI references
        btnScan = findViewById(R.id.btnScan)
        btnWebsite = findViewById(R.id.btnWebsite)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        txtLoading = findViewById(R.id.txtLoading)
        chkSystemApps = findViewById(R.id.chkSystemApps)

        recyclerView.layoutManager = LinearLayoutManager(this)
        appAdapter = AppAdapter(listOf())
        recyclerView.adapter = appAdapter

        // Scan apps
        btnScan.setOnClickListener {
            loadInstalledApps()
        }

        // Open website in WebView
        btnWebsite.setOnClickListener {
            val intent = Intent(this, WebViewActivity::class.java)
            startActivity(intent)
        }

        // Toggle system apps
        chkSystemApps.setOnCheckedChangeListener { _, isChecked ->
            showSystemApps = isChecked
            filterApps()
        }
    }

    private fun loadInstalledApps() {
        // Show loading
        progressBar.visibility = View.VISIBLE
        txtLoading.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = mutableListOf<AppInfo>()

            for (app in packages) {
                val name = pm.getApplicationLabel(app).toString()
                val pkgName = app.packageName
                val icon = try {
                    pm.getApplicationIcon(app)
                } catch (e: Exception) {
                    null
                } ?: resources.getDrawable(R.mipmap.ic_launcher, theme)

                val permissions = try {
                    pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList()
                        ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                val risk = calculateRisk(permissions)

                list.add(AppInfo(name, pkgName, permissions, risk, icon, isSystemApp(app)))
            }

            // Save all apps
            allApps = list

            withContext(Dispatchers.Main) {
                // Filter + update UI
                filterApps()
                progressBar.visibility = View.GONE
                txtLoading.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun filterApps() {
        val filtered = if (showSystemApps) {
            allApps
        } else {
            allApps.filter { !it.isSystemApp }
        }

        // sort by risk > name
        val sorted = filtered.sortedWith(compareByDescending<AppInfo> { it.riskLevel }.thenBy { it.name })
        appAdapter.updateData(sorted)
    }

    private fun isSystemApp(app: ApplicationInfo): Boolean {
        return (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
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

