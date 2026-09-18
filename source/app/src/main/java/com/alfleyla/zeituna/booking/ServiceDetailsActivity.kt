package com.alfleyla.zeituna.booking

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.alfleyla.zeituna.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class ServiceDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_details)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarDetails)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val tvTitle = findViewById<TextView>(R.id.tvDetailTitle)
        val tvCourse = findViewById<TextView>(R.id.tvDetailCourse)
        val tvExpiration = findViewById<TextView>(R.id.tvExpirationDate)
        val tvDescription = findViewById<TextView>(R.id.tvDetailDescription)
        val btnBuy = findViewById<MaterialButton>(R.id.btnBuyConfirm)
        val ivBackground = findViewById<ImageView>(R.id.ivBackgroundStyle)
        val ivConversation = findViewById<ImageView>(R.id.ivConversationStyle)

        val serviceName = intent.getStringExtra("SERVICE_NAME") ?: "Lesson"
        val courseName = intent.getStringExtra("COURSE_NAME") ?: ""
        val validityDays = intent.getIntExtra("VALIDITY_DAYS", 30)
        val description = intent.getStringExtra("DESCRIPTION")
        
        tvTitle.text = serviceName
        tvCourse.text = courseName
        tvExpiration.text = "Expires in: $validityDays days after purchase"
        
        // Handle background based on course name
        if (courseName.contains("Egyptian", ignoreCase = true)) {
            ivBackground.setImageResource(R.drawable.bg)
        } else if (courseName.contains("MSA", ignoreCase = true)) {
            ivBackground.setImageResource(R.drawable.style)
        }

        // Show conversation image if course name contains "conversation"
        if (courseName.contains("conversation", ignoreCase = true)) {
            ivConversation.visibility = View.VISIBLE
        } else {
            ivConversation.visibility = View.GONE
        }

        if (!description.isNullOrEmpty()) {
            tvDescription.text = description
        } else {
            tvDescription.text = "No description available."
        }

        btnBuy.setOnClickListener {
            val intent = Intent(this, CalendarBookingActivity::class.java).apply {
                putExtras(this@ServiceDetailsActivity.intent)
            }
            startActivity(intent)
            finish()
        }
    }
}
