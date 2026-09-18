package com.alfleyla.zeituna.booking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.data.models.LessonService
import java.util.Locale

class ServiceAdapter(
    private val services: List<LessonService>,
    private val onServiceSelected: (LessonService) -> Unit
) : RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder>() {

    class ServiceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvServiceTitle)
        val tvCourse: TextView = view.findViewById(R.id.tvCourseName)
        val tvTeacher: TextView = view.findViewById(R.id.tvTeacherName)
        val tvPrice: TextView = view.findViewById(R.id.tvServicePrice)
        val tvPricePerLesson: TextView = view.findViewById(R.id.tvPricePerLesson)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service, parent, false)
        return ServiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val service = services[position]
        holder.tvTitle.text = service.displayLabel
        holder.tvCourse.text = service.course_name
        holder.tvTeacher.text = "Teacher: ${service.teacher ?: "Unknown"}"
        holder.tvPrice.text = String.format(Locale.US, "$%.2f", service.price)
        holder.tvPricePerLesson.text = String.format(Locale.US, "$%.2f/lesson", service.pricePerLesson)

        holder.itemView.setOnClickListener {
            onServiceSelected(service)
        }
    }

    override fun getItemCount() = services.size
}
