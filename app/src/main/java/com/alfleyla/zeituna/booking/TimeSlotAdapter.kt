package com.alfleyla.zeituna.booking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.data.models.BookititSlot
import com.google.android.material.card.MaterialCardView

class TimeSlotAdapter(
    private val slots: List<BookititSlot>,
    private val onSlotSelected: (BookititSlot) -> Unit
) : RecyclerView.Adapter<TimeSlotAdapter.TimeViewHolder>() {

    private var selectedPosition = -1

    class TimeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardTimeSlot)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_time_slot, parent, false)
        return TimeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        val slot = slots[position]
        
        // FIX: Use take(5) to ensure we get HH:mm (like 12:30) 
        // and don't cut off the minutes by mistake.
        val displayTime = slot.start.take(5)
        
        holder.tvTime.text = displayTime

        val isSelected = selectedPosition == position
        
        if (isSelected) {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.turquoise_primary))
            holder.tvTime.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
        } else {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
            holder.tvTime.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.turquoise_primary))
        }

        holder.itemView.setOnClickListener {
            val previousSelection = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previousSelection)
            notifyItemChanged(selectedPosition)
            onSlotSelected(slot)
        }
    }

    override fun getItemCount() = slots.size
}
