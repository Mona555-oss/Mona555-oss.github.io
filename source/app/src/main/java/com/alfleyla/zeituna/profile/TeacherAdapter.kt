package com.alfleyla.zeituna.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.data.models.Profile

class TeacherAdapter(private val teachers: List<Profile>) :
    RecyclerView.Adapter<TeacherAdapter.TeacherViewHolder>() {

    class TeacherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvTeacherName)
        val tvEmail: TextView = view.findViewById(R.id.tvTeacherEmail)
        val tvIntro: TextView = view.findViewById(R.id.tvTeacherIntro)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher, parent, false)
        return TeacherViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        val teacher = teachers[position]
        holder.tvName.text = teacher.full_name ?: "Teacher"
        holder.tvEmail.text = teacher.email ?: ""
        holder.tvIntro.text = teacher.introduction ?: "No introduction provided."
    }

    override fun getItemCount() = teachers.size
}
