package com.alfleyla.zeituna.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.data.models.Booking
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PackageAdapter(
    private val packages: List<Booking>,
    private val onPackageClick: (Booking) -> Unit
) : RecyclerView.Adapter<PackageAdapter.PackageViewHolder>() {

    class PackageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPackageName)
        val tvStatus: TextView = view.findViewById(R.id.tvPackageStatus)
        val tvRemaining: TextView = view.findViewById(R.id.tvRemainingLessons)
        val tvBoughtAt: TextView = view.findViewById(R.id.tvBoughtAt)
        val tvExpiresAt: TextView = view.findViewById(R.id.tvExpiresAt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_package, parent, false)
        return PackageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        val pkg = packages[position]

        // Show Student Name if present (for Teacher Dashboard)
        val displayName = pkg.service?.displayLabel ?: "Lesson Package"
        holder.tvName.text = if (!pkg.student_name.isNullOrEmpty()) {
            "$displayName - ${pkg.student_name}"
        } else {
            displayName
        }

        holder.tvStatus.text = "Status: ${pkg.status.replaceFirstChar { it.uppercase() }}"
        holder.tvRemaining.text = pkg.remaining_lessons.toString()
        
        if (!pkg.created_at.isNullOrEmpty()) {
            holder.tvBoughtAt.text = "bought: ${formatTimestamp(pkg.created_at)}"
        } else {
            holder.tvBoughtAt.text = "bought: -"
        }

        if (!pkg.expires_at.isNullOrEmpty()) {
            holder.tvExpiresAt.text = "expires: ${formatTimestamp(pkg.expires_at)}"
        } else {
            holder.tvExpiresAt.text = "expires: -"
        }
        
        holder.itemView.setOnClickListener { onPackageClick(pkg) }
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val normalized = timestamp.replace(" ", "T").substringBefore("+").substringBefore("Z")
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val outputFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            val date = inputFormat.parse(normalized)
            if (date != null) outputFormat.format(date) else timestamp
        } catch (e: Exception) {
            timestamp.take(16).replace("T", " ")
        }
    }

    override fun getItemCount() = packages.size
}
