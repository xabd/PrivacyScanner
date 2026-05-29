package nodomain.xabd.privacyscanner

import android.annotation.SuppressLint
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
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import androidx.core.graphics.toColorInt

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
        val label = (app.riskLevel).lowercase(Locale.getDefault())

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
        val displayRisk = if (app.source.isBlank())
            "$emoji ${app.riskLevel}"
        else
            "$emoji ${app.riskLevel} • ${app.source}"

        holder.risk.text = displayRisk

        // 🎨 Dynamic text + background colors
        val textColor: Int
        val backgroundColor: Int

        when {
            label.contains("high") -> {
                textColor = "#E53935".toColorInt() // strong red
                backgroundColor = if (isDarkMode)
                    "#33FF5252".toColorInt()
                else
                    "#FFFFEBEE".toColorInt()
            }
            label.contains("medium") -> {
                textColor = "#FB8C00".toColorInt() // amber
                backgroundColor = if (isDarkMode)
                    "#33FFA000".toColorInt()
                else
                    "#FFFFF3E0".toColorInt()
            }
            label.contains("low") -> {
                textColor = "#FFD54F".toColorInt() // yellow accent
                backgroundColor = if (isDarkMode)
                    "#33FFD54F".toColorInt()
                else
                    "#FFFFF8E1".toColorInt()
            }
            label.contains("safe") -> {
                textColor = "#43A047".toColorInt() // ✅ dark green
                backgroundColor = if (isDarkMode)
                    "#334CAF50".toColorInt()
                else
                    "#FFE8F5E9".toColorInt() // pale green background for light mode
            }
            label.contains("trusted") -> {
                textColor = "#1976D2".toColorInt() // blue
                backgroundColor = if (isDarkMode)
                    "#331976D2".toColorInt()
                else
                    "#FFE3F2FD".toColorInt()
            }
            else -> {
                textColor = if (isDarkMode) Color.LTGRAY else Color.DKGRAY
                backgroundColor = if (isDarkMode)
                    "#222222".toColorInt()
                else
                    "#FFF5F5F5".toColorInt()
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

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newList: List<AppInfo>) {
        apps = newList
        notifyDataSetChanged()
    }
}

