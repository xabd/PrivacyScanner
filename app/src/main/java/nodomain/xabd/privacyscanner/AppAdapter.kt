package nodomain.xabd.privacyscanner

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AppAdapter(private var apps: List<AppInfo>) :
    RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.appCard)
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val pkg: TextView = view.findViewById(R.id.appPackage)
        val risk: TextView = view.findViewById(R.id.appRisk)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.app_item, parent, false)
        return AppViewHolder(v)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val context = holder.itemView.context
        val app = apps[position]
        val isDarkMode = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.name
        holder.pkg.text = app.packageName

        // Normalize label for robust color detection
        val label = (app.riskLevel ?: "").lowercase(Locale.getDefault())

        // 🧩 Risk emoji mapping
        val emoji = when {
            label.contains("high") -> "🔴"
            label.contains("medium") -> "🟠"
            label.contains("low") -> "🟡"
            label.contains("safe") -> "🟢"
            label.contains("trusted") -> "🔵"
            else -> "⚪"
        }

        // Risk + Source text
        val displayRisk = if (app.source.isNullOrBlank())
            "$emoji ${app.riskLevel}"
        else
            "$emoji ${app.riskLevel} • ${app.source}"

        holder.risk.text = displayRisk

        // 🎨 Dynamic text + background colors
        val textColor: Int
        val backgroundColor: Int

        when {
            label.contains("high") -> {
                textColor = Color.parseColor("#E53935") // strong red
                backgroundColor = if (isDarkMode)
                    Color.parseColor("#33FF5252")
                else
                    Color.parseColor("#FFFFEBEE")
            }
            label.contains("medium") -> {
                textColor = Color.parseColor("#FB8C00") // amber
                backgroundColor = if (isDarkMode)
                    Color.parseColor("#33FFA000")
                else
                    Color.parseColor("#FFFFF3E0")
            }
            label.contains("low") -> {
                textColor = Color.parseColor("#FFD54F") // yellow accent
                backgroundColor = if (isDarkMode)
                    Color.parseColor("#33FFD54F")
                else
                    Color.parseColor("#FFFFF8E1")
            }
            label.contains("safe") -> {
                textColor = Color.parseColor("#43A047") // ✅ dark green
                backgroundColor = if (isDarkMode)
                    Color.parseColor("#334CAF50")
                else
                    Color.parseColor("#FFE8F5E9") // pale green background for light mode
            }
            label.contains("trusted") -> {
                textColor = Color.parseColor("#1976D2") // blue
                backgroundColor = if (isDarkMode)
                    Color.parseColor("#331976D2")
                else
                    Color.parseColor("#FFE3F2FD")
            }
            else -> {
                textColor = if (isDarkMode) Color.LTGRAY else Color.DKGRAY
                backgroundColor = if (isDarkMode)
                    Color.parseColor("#222222")
                else
                    Color.parseColor("#FFF5F5F5")
            }
        }

        holder.risk.setTextColor(textColor)
        holder.card.setCardBackgroundColor(backgroundColor)

        // Typography emphasis
        holder.name.setTypeface(null, Typeface.BOLD)
        holder.risk.setTypeface(null, Typeface.BOLD)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, AppDetailActivity::class.java).apply {
                putExtra("PACKAGE_NAME", app.packageName)
                putStringArrayListExtra("PERMISSIONS", ArrayList(app.permissions))
                putExtra("RISK_LEVEL", app.riskLevel)
                putExtra("SOURCE", app.source)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateData(newList: List<AppInfo>) {
        apps = newList
        notifyDataSetChanged()
    }
}

