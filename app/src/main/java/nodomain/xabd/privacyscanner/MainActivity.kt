package nodomain.xabd.privacyscanner

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
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

        // 🔍 Scan apps
        btnScan.setOnClickListener { loadInstalledApps() }

        // 🌐 Open website externally (no internet, uses browser)
        btnWebsite.setOnClickListener {
            val url = "https://digital-escape-tools.vercel.app"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        // 🧩 Toggle system apps visibility
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

                // ✅ Only granted permissions, not just declared ones
                val grantedPermissions = try {
                    val pkgInfo = pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS)
                    val requested = pkgInfo.requestedPermissions
                    val flags = pkgInfo.requestedPermissionsFlags
                    val granted = mutableListOf<String>()

                    if (requested != null && flags != null) {
                        for (i in requested.indices) {
                            if (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) {
                                granted.add(requested[i])
                            }
                        }
                    }
                    granted
                } catch (e: Exception) {
                    emptyList()
                }

                val (risk, source) = RiskCalculator.calculate(this@MainActivity, pkgName, grantedPermissions)

                list.add(
                    AppInfo(
                        name,
                        pkgName,
                        grantedPermissions,
                        risk,
                        icon,
                        isSystemApp(app),
                        source
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

    private fun riskScore(risk: String): Int = when {
        risk.contains("High") -> 3
        risk.contains("Medium") -> 2
        risk.contains("Low") -> 1
        risk.contains("Trusted") -> 4
        else -> 0
    }
}
