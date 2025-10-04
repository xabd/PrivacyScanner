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
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {

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
        // Follow system dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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
        btnScan.setOnClickListener { loadInstalledApps() }

        // Open website in WebView
        btnWebsite.setOnClickListener {
            startActivity(Intent(this, WebViewActivity::class.java))
        }

        // Toggle system apps
        chkSystemApps.setOnCheckedChangeListener { _, isChecked ->
            showSystemApps = isChecked
            filterApps()
        }
    }

    private fun loadInstalledApps() {
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
                    pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS)
                        .requestedPermissions?.toList()
                        ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                // ✅ Get both risk + source
                val (risk, source) = RiskCalculator.calculate(this@MainActivity, pkgName, permissions)

                // 🔥 Update AppInfo to store source as well
                list.add(
                    AppInfo(
                        name,
                        pkgName,
                        permissions,
                        risk,
                        icon,
                        isSystemApp(app),
                        source // new field
                    )
                )
            }

            allApps = list

            withContext(Dispatchers.Main) {
                filterApps()
                progressBar.visibility = View.GONE
                txtLoading.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun filterApps() {
        val filtered = if (showSystemApps) allApps else allApps.filter { !it.isSystemApp }
        val sorted = filtered.sortedWith(
            compareByDescending<AppInfo> { riskScore(it.riskLevel) }
                .thenBy { it.name.lowercase() }
        )
        appAdapter.updateData(sorted)
    }

    private fun isSystemApp(app: ApplicationInfo) =
        (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    // Helper for proper sorting by severity
    private fun riskScore(risk: String): Int = when {
        risk.contains("High") -> 3
        risk.contains("Medium") -> 2
        risk.contains("Low") -> 1
        risk.contains("Trusted") -> 4 // ensure trusted apps appear at top
        else -> 0
    }
}



