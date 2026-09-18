package com.alfleyla.zeituna.profile

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.Profile
import com.google.android.material.appbar.MaterialToolbar
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class TeachersListActivity : AppCompatActivity() {

    private val TAG = "TeachersList"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teachers_list)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarTeachers)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val rvTeachers = findViewById<RecyclerView>(R.id.rvTeachers)
        val progressBar = findViewById<ProgressBar>(R.id.pbTeachers)
        val tvEmpty = findViewById<TextView>(R.id.tvEmptyTeachers)
        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshTeachers)

        rvTeachers.layoutManager = LinearLayoutManager(this)

        swipeRefresh.setOnRefreshListener {
            fetchTeachers(rvTeachers, progressBar, tvEmpty, swipeRefresh)
        }

        Log.d(TAG, "Activity Created - Triggering fetchTeachers")
        fetchTeachers(rvTeachers, progressBar, tvEmpty, swipeRefresh)
    }

    private fun fetchTeachers(rv: RecyclerView, pb: ProgressBar, tvEmpty: TextView, swipeRefresh: SwipeRefreshLayout) {
        lifecycleScope.launch {
            if (!swipeRefresh.isRefreshing) {
                pb.visibility = View.VISIBLE
            }
            
            Log.d(TAG, "Fetch started - querying Postgrest for role='teacher'")
            try {
                val response = SupabaseClientObj.client.postgrest["profiles"]
                    .select {
                        filter {
                            eq("role", "teacher")
                        }
                    }
                
                val teachers = response.decodeList<Profile>()
                Log.d(TAG, "Query successful. Decoded ${teachers.size} teachers.")

                if (teachers.isEmpty()) {
                    Log.w(TAG, "The list is empty. Check if any profiles have role='teacher' in your database.")
                    tvEmpty.visibility = View.VISIBLE
                    rv.visibility = View.GONE
                } else {
                    Log.d(TAG, "Displaying list in RecyclerView.")
                    tvEmpty.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                    rv.adapter = TeacherAdapter(teachers)
                }
            } catch (e: Exception) {
                Log.e(TAG, "EXCEPTION during fetch: ${e.message}", e)
                tvEmpty.text = "Error loading teachers: ${e.message}"
                tvEmpty.visibility = View.VISIBLE
            } finally {
                pb.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }
}
