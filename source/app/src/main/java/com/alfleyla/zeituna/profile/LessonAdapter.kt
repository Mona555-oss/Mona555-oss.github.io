package com.alfleyla.zeituna.profile

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.data.models.BookititEvent
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LessonAdapter(
    private val lessons: List<BookititEvent>,
    private val onUnscheduleClick: (BookititEvent) -> Unit
) : RecyclerView.Adapter<LessonAdapter.LessonViewHolder>() {

    class LessonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: MaterialCardView = view.findViewById(R.id.lessonCardRoot)
        val tvDate: TextView = view.findViewById(R.id.tvLessonDate)
        val tvTime: TextView = view.findViewById(R.id.tvLessonTime)
        val tvPreviousLabel: TextView = view.findViewById(R.id.tvPreviousLabel)
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvServiceName: TextView = view.findViewById(R.id.tvLessonServiceName)
        val tvStatus: TextView = view.findViewById(R.id.tvLessonStatus)
        val tvScheduledAt: TextView = view.findViewById(R.id.tvScheduledAt)
        val btnUnschedule: Button = view.findViewById(R.id.btnUnschedule)
        val divider: View = view.findViewById(R.id.lessonDivider)
        
        // Reason views
        val layoutReason: LinearLayout = view.findViewById(R.id.layoutReason)
        val tvReason: TextView = view.findViewById(R.id.tvReason)
        val tvMoreReason: TextView = view.findViewById(R.id.tvMoreReason)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lesson, parent, false)
        return LessonViewHolder(view)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        val lesson = lessons[position]
        val status = lesson.status?.lowercase()
        val isUnscheduled = status == "unscheduled"
        val isCompleted = status == "completed"
        
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = SimpleDateFormat("dd MMM", Locale.US)
        
        val dateStr = try {
            val date = inputFormat.parse(lesson.date)
            if (date != null) outputFormat.format(date) else lesson.date
        } catch (e: Exception) {
            lesson.date
        }

        holder.tvDate.text = "$dateStr"
        
        if (!lesson.end.isNullOrEmpty()) {
            holder.tvTime.text = "${lesson.start.take(5)} - ${lesson.end.take(5)}"
        } else {
            holder.tvTime.text = lesson.start.take(5)
        }
        
        // Show student name if available (Teacher Dashboard)
        if (!lesson.client_name.isNullOrEmpty()) {
            holder.tvStudentName.visibility = View.VISIBLE
            holder.tvStudentName.text = lesson.client_name
        } else {
            holder.tvStudentName.visibility = View.GONE
        }

        holder.tvServiceName.text = lesson.service_name ?: "Lesson"

        // Visual handling for Unscheduled or Completed lessons
        if (isUnscheduled || isCompleted) {
            holder.cardRoot.alpha = 0.6f
            holder.cardRoot.setStrokeColor(Color.parseColor("#CCCCCC"))
            holder.divider.setBackgroundColor(Color.parseColor("#CCCCCC"))
            holder.tvTime.setTextColor(Color.GRAY)
            holder.btnUnschedule.visibility = View.GONE
            
            if (isUnscheduled) {
                holder.tvPreviousLabel.visibility = View.VISIBLE
                holder.tvStatus.text = "Status: Unscheduled"
                holder.tvStatus.setTextColor(Color.parseColor("#D32F2F")) // Red tint for cancelled
                
                // Show Unschedule Reason
                if (!lesson.unschedule_reason.isNullOrBlank()) {
                    holder.layoutReason.visibility = View.VISIBLE
                    val reasonContent = lesson.unschedule_reason!!
                    val prefix = "Reason: "
                    
                    if (reasonContent.length > 50) {
                        val truncated = prefix + reasonContent.take(50) + "..."
                        holder.tvReason.text = truncated
                        holder.tvMoreReason.visibility = View.VISIBLE
                        holder.tvMoreReason.setOnClickListener {
                            holder.tvReason.text = prefix + reasonContent
                            holder.tvMoreReason.visibility = View.GONE
                        }
                    } else {
                        holder.tvReason.text = prefix + reasonContent
                        holder.tvMoreReason.visibility = View.GONE
                    }
                } else {
                    holder.layoutReason.visibility = View.GONE
                }
                
            } else {
                holder.tvPreviousLabel.visibility = View.GONE
                holder.tvStatus.text = "Status: Completed"
                holder.tvStatus.setTextColor(Color.GRAY)
                holder.layoutReason.visibility = View.GONE
            }
        } else {
            // Standard "Upcoming" active lesson
            holder.cardRoot.alpha = 1.0f
            holder.cardRoot.setStrokeColor(holder.itemView.context.getColor(R.color.turquoise_light))
            holder.divider.setBackgroundColor(holder.itemView.context.getColor(R.color.turquoise_light))
            holder.tvPreviousLabel.visibility = View.GONE
            holder.tvStatus.text = "Status: ${lesson.status ?: "Confirmed"}"
            holder.tvStatus.setTextColor(holder.itemView.context.getColor(R.color.turquoise_dark))
            holder.btnUnschedule.visibility = View.VISIBLE
            holder.tvTime.setTextColor(holder.itemView.context.getColor(R.color.turquoise_dark))
            holder.layoutReason.visibility = View.GONE

            // Check if lesson is expired for the button action
            val now = Calendar.getInstance().time
            val isExpired = isExpired(lesson.expires_at, now)

            if (isExpired) {
                holder.btnUnschedule.isEnabled = false
                holder.btnUnschedule.alpha = 0.5f
            } else {
                holder.btnUnschedule.isEnabled = true
                holder.btnUnschedule.alpha = 1.0f
                holder.btnUnschedule.setOnClickListener { onUnscheduleClick(lesson) }
            }
        }

        if (!lesson.created_at.isNullOrEmpty()) {
            holder.tvScheduledAt.text = "scheduled: ${formatTimestamp(lesson.created_at!!)}"
        } else {
            holder.tvScheduledAt.text = "scheduled: -"
        }
    }

    private fun isExpired(expiresAt: String?, now: Date): Boolean {
        if (expiresAt == null) return false
        return try {
            val normalized = expiresAt.replace(" ", "T").substringBefore("+").substringBefore("Z")
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val expiryDate = format.parse(normalized)
            expiryDate != null && expiryDate.before(now)
        } catch (e: Exception) { false }
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val outputFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            if (date != null) outputFormat.format(date) else timestamp
        } catch (e: Exception) {
            timestamp.take(16).replace("T", " ")
        }
    }

    override fun getItemCount() = lessons.size
}
