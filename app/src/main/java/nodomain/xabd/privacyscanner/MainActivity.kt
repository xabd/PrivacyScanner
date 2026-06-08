package nodomain.xabd.privacyscanner

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class MainActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var txtLoading: TextView
    private lateinit var chkSystemApps: CheckBox
    private lateinit var btnScan: Button
    private lateinit var btnWebsite: Button
    private lateinit var txtHeader: TextView
    private lateinit var ivInfo: ImageView

    private var showSystemApps = false
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🌙 Follow system dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.rootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = bars.top,
                bottom = bars.bottom
            )
            insets
        }

        // 🟪 Header title
        txtHeader = findViewById(R.id.txtHeader)
        txtHeader.text = getString(R.string.app_name)

        // 🆕 Info icon (menu)
        ivInfo = findViewById(R.id.ivInfo)
        ivInfo.setOnClickListener {
            val popup = PopupMenu(this, ivInfo)
            popup.menu.apply {
                add("🔗 View Source Code")
                add("🐞 Report an Issue")
                add("💖 Donate / Support")
            }
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "🔗 View Source Code" -> openLink("https://github.com/xabd/PrivacyScanner")
                    "🐞 Report an Issue" -> openLink("https://github.com/xabd/PrivacyScanner/issues")
                    "💖 Donate / Support" -> openLink("https://ko-fi.com/digitalescape")
                }
                true
            }
            popup.show()
        }

        // 🔹 UI setup
        btnScan = findViewById(R.id.btnScan)
        btnWebsite = findViewById(R.id.btnWebsite)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        txtLoading = findViewById(R.id.txtLoading)
        chkSystemApps = findViewById(R.id.chkSystemApps)

        recyclerView.layoutManager = LinearLayoutManager(this)
        appAdapter = AppAdapter(listOf())
        recyclerView.adapter = appAdapter

        btnScan.setOnClickListener { loadInstalledApps() }

        btnWebsite.setOnClickListener {
            val url = "https://digital-escape-tools.vercel.app"
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }

        chkSystemApps.setOnCheckedChangeListener { _, isChecked ->
            showSystemApps = isChecked
            filterApps()
        }
    }

    private fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }

    private fun loadInstalledApps() {
        progressBar.visibility = View.VISIBLE
        txtLoading.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        btnScan.isEnabled = false
        txtLoading.text = getString(R.string.scanning_installed_apps_please_wait)

        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val resultList = mutableListOf<AppInfo>()

            for ((index, app) in apps.withIndex()) {
                try {
                    val name = pm.getApplicationLabel(app).toString()
                    val pkgName = app.packageName
                    val icon = pm.getApplicationIcon(app)
                    val isSystem = isSystemApp(app)
                    val grantedPermissions = getGrantedPermissions(pm, pkgName)

                    val (risk, source) =
                        RiskCalculator.calculate(this@MainActivity, pkgName, grantedPermissions)

                    resultList.add(
                        AppInfo(
                            name = name,
                            packageName = pkgName,
                            permissions = grantedPermissions,
                            riskLevel = risk,
                            icon = icon,
                            isSystemApp = isSystem,
                            source = source
                        )
                    )

                    if (index % 15 == 0) {
                        withContext(Dispatchers.Main) {
                            txtLoading.text =
                                getString(R.string.scanning_action_label, index + 1, apps.size)
                        }
                    }

                } catch (_: Exception) {
                }
            }

            allApps = resultList

            withContext(Dispatchers.Main) {
                filterApps()
                progressBar.visibility = View.GONE
                txtLoading.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                btnScan.isEnabled = true
                Toast.makeText(
                    this@MainActivity,
                    "✅ Scan completed: ${allApps.size} apps",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getGrantedPermissions(pm: PackageManager, pkgName: String): List<String> {
        return try {
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun filterApps() {
        val filtered = if (showSystemApps) allApps else allApps.filter { !it.isSystemApp }
        val sorted = filtered.sortedWith(
            compareByDescending<AppInfo> { riskScore(it.riskLevel) }
                .thenBy { it.name.lowercase() }
        )
        appAdapter.updateData(sorted)
        txtLoading.text = resources.getQuantityString(
            R.plurals.showing_apps_label,
            sorted.size,
            sorted.size
        )
    }

    private fun isSystemApp(app: ApplicationInfo) =
        (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    private fun riskScore(risk: String): Int = when {
        risk.contains("high", true) -> 5
        risk.contains("medium", true) -> 4
        risk.contains("low", true) -> 3
        risk.contains("safe", true) -> 2
        risk.contains("trusted", true) -> 1
        else -> 0
    }
}
