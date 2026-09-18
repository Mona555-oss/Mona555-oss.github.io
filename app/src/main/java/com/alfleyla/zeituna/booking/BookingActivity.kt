package com.alfleyla.zeituna.booking

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.auth.LoginActivity
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.profile.ProfileActivity
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class BookingActivity : AppCompatActivity() {

    private lateinit var viewModel: BookingViewModel
    private var adapter: ServiceAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking)

        val root = findViewById<View>(R.id.main_booking_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val rvServices = findViewById<RecyclerView>(R.id.rvServices)
        rvServices.layoutManager = LinearLayoutManager(this)

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val btnOfProfile = findViewById<MaterialButton>(R.id.btnOfProfile)
        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)

        btnOfProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            logoutUser()
        }

        viewModel = ViewModelProvider(this)[BookingViewModel::class.java]

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.fetchServices()
        }

        viewModel.services.observe(this) { services ->
            if (services != null) {
                adapter = ServiceAdapter(services) { selectedService ->
                    val bookititId = selectedService.bookitit_service_id
                    val supabaseId = selectedService.id
                    val agendaId = selectedService.bookitit_agenda_id

                    if (!bookititId.isNullOrEmpty() && !supabaseId.isNullOrEmpty() && !agendaId.isNullOrEmpty()) {
                        val intent = Intent(this, ServiceDetailsActivity::class.java).apply {
                            putExtra("BOOKITIT_SERVICE_ID", bookititId)
                            putExtra("SUPABASE_SERVICE_ID", supabaseId)
                            putExtra("BOOKITIT_AGENDA_ID", agendaId)
                            putExtra("SERVICE_NAME", selectedService.displayLabel)
                            putExtra("COURSE_NAME", selectedService.course_name)
                            putExtra("SERVICE_PRICE", selectedService.price)
                            putExtra("SERVICE_DURATION", selectedService.duration)
                            putExtra("SERVICE_COUNT", selectedService.count)
                            putExtra("VALIDITY_DAYS", selectedService.validity_days ?: 30)
                            putExtra("DESCRIPTION", selectedService.description)
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Service details (IDs) missing", Toast.LENGTH_SHORT).show()
                    }
                }
                rvServices.adapter = adapter
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            // Use the swipe refresh indicator instead of the central progress bar if it's a refresh action
            if (swipeRefreshLayout.isRefreshing) {
                if (!isLoading) {
                    swipeRefreshLayout.isRefreshing = false
                }
            } else {
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        viewModel.fetchServices()
    }

    private fun logoutUser() {
        lifecycleScope.launch {
            try {
                SupabaseClientObj.client.auth.signOut()
                Toast.makeText(this@BookingActivity, "Logged out", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@BookingActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("BookingActivity", "Logout failed: ${e.message}")
            }
        }
    }
}
