package com.example.privacyscanner

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(private var apps: List<AppInfo>) :
    RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.name
        holder.pkg.text = app.packageName
        holder.risk.text = app.riskLevel

        // color risk text
        when {
            app.riskLevel.contains("High", ignoreCase = true) ->
                holder.risk.setTextColor(Color.parseColor("#D32F2F"))
            app.riskLevel.contains("Medium", ignoreCase = true) ->
                holder.risk.setTextColor(Color.parseColor("#FFA000"))
            app.riskLevel.contains("Low", ignoreCase = true) ->
                holder.risk.setTextColor(Color.parseColor("#388E3C"))
            else ->
                holder.risk.setTextColor(Color.DKGRAY)
        }

        // click → open AppDetailActivity
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, AppDetailActivity::class.java)
            intent.putExtra("PACKAGE_NAME", app.packageName)
            intent.putStringArrayListExtra("PERMISSIONS", ArrayList(app.permissions))
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateData(newList: List<AppInfo>) {
        apps = newList
        notifyDataSetChanged()
    }
}
