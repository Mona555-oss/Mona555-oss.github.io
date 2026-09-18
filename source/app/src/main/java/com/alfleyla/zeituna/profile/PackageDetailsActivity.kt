package com.alfleyla.zeituna.profile

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.booking.CalendarBookingActivity
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class PackageDetailsActivity : AppCompatActivity() {

    private var currentExpiresAt: String? = null
    private var bookingId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_details)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        bookingId = intent.getStringExtra("BOOKING_ID") ?: ""
        val serviceName = intent.getStringExtra("SERVICE_NAME") ?: ""
        val remainingLessons = intent.getIntExtra("REMAINING_LESSONS", 0)
        val bookititServiceId = intent.getStringExtra("BOOKITIT_SERVICE_ID") ?: ""
        val bookititAgendaId = intent.getStringExtra("BOOKITIT_AGENDA_ID") ?: ""
        val serviceDuration = intent.getIntExtra("SERVICE_DURATION", 30)
        currentExpiresAt = intent.getStringExtra("EXPIRES_AT")
        val userRole = intent.getStringExtra("USER_ROLE")

        val tvHeader = findViewById<TextView>(R.id.tvPackageHeader)
        val tvExpiry = findViewById<TextView>(R.id.tvPackageExpiry)
        val btnExtend = findViewById<MaterialButton>(R.id.btnExtendPackage)

        tvHeader.text = "$serviceName\n($remainingLessons lessons remaining)"
        updateExpiryText(tvExpiry)

        if (userRole == "teacher") {
            btnExtend.visibility = View.VISIBLE
            btnExtend.setOnClickListener {
                showExtendDialog(tvExpiry)
            }
        }

        val rvLessons = findViewById<RecyclerView>(R.id.rvRemainingLessons)
        rvLessons.layoutManager = LinearLayoutManager(this)
        
        val lessonsList = (1..remainingLessons).toList()
        rvLessons.adapter = RemainingLessonAdapter(lessonsList) { lessonNumber ->
            val intent = Intent(this, CalendarBookingActivity::class.java).apply {
                putExtra("BOOKITIT_SERVICE_ID", bookititServiceId)
                putExtra("BOOKITIT_AGENDA_ID", bookititAgendaId)
                putExtra("SUPABASE_SERVICE_ID", "")
                putExtra("EXISTING_BOOKING_ID", bookingId) 
                putExtra("SERVICE_NAME", "$serviceName (Lesson $lessonNumber)")
                putExtra("SERVICE_DURATION", serviceDuration)
                putExtra("IS_PACKAGE_LESSON", true)
            }
            startActivity(intent)
        }
    }

    private fun updateExpiryText(tvExpiry: TextView) {
        if (!currentExpiresAt.isNullOrEmpty()) {
            tvExpiry.text = "expires: ${formatTimestamp(currentExpiresAt!!)}"
        } else {
            tvExpiry.text = "expires: -"
        }
    }

    private fun showExtendDialog(tvExpiry: TextView) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Enter number of days"

        val dialog = AlertDialog.Builder(this)
            .setTitle("Extend Package")
            .setMessage("How many days would you like to add to this package's expiration date?")
            .setView(input)
            .setPositiveButton("Extend") { _, _ ->
                val daysStr = input.text.toString()
                if (daysStr.isNotEmpty()) {
                    val days = daysStr.toInt()
                    extendPackage(currentExpiresAt, days, tvExpiry)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun extendPackage(expirationDate: String?, days: Int, tvExpiry: TextView) {
        lifecycleScope.launch {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                
                if (!expirationDate.isNullOrEmpty()) {
                    val normalized = expirationDate.replace(" ", "T").substringBefore("+").substringBefore("Z")
                    val date = sdf.parse(normalized)
                    if (date != null) {
                        calendar.time = date
                    }
                }
                
                calendar.add(Calendar.DAY_OF_YEAR, days)
                val newExpiry = sdf.format(calendar.time) + "Z"

                SupabaseClientObj.client.postgrest["Bookings"].update(
                    mapOf("expires_at" to newExpiry)
                ) {
                    filter { eq("id", bookingId) }
                }

                currentExpiresAt = newExpiry
                updateExpiryText(tvExpiry)
                Toast.makeText(this@PackageDetailsActivity, "Package extended by $days days", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@PackageDetailsActivity, "Error extending package: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
}
