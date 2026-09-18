package com.alfleyla.zeituna.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alfleyla.zeituna.R
import com.google.android.material.card.MaterialCardView

class RemainingLessonAdapter(
    private val lessons: List<Int>,
    private val onScheduleClick: (Int) -> Unit
) : RecyclerView.Adapter<RemainingLessonAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardRemainingLesson)
        val tvLabel: TextView = view.findViewById(R.id.tvLessonLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_button, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val lessonNumber = lessons[position]
        holder.tvLabel.text = "Lesson $lessonNumber"
        
        // Handle click on the entire card for better UX and visual feedback
        holder.card.setOnClickListener { onScheduleClick(lessonNumber) }
    }

    override fun getItemCount() = lessons.size
}
