package com.alfleyla.zeituna.profile

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.auth.LoginActivity
import com.alfleyla.zeituna.booking.BookingActivity
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.BookititEvent
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var packageAdapter: PackageAdapter
    private lateinit var upcomingLessonAdapter: LessonAdapter
    private lateinit var completedLessonAdapter: LessonAdapter
    private lateinit var unscheduledLessonAdapter: LessonAdapter

    private val TERMS_URL = "https://github.com/Mona555-oss/silentspace-legal/blob/main/TERMS_OF_SERVICE.md"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Fix Status Bar overlap
        val root = findViewById<View>(R.id.main_profile_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val btnLogout = findViewById<MaterialButton>(R.id.btnLogoutProfile)
        btnLogout.setOnClickListener {
            logoutUser()
        }

        val btnMyAccount = findViewById<MaterialButton>(R.id.btnMyAccount)
        btnMyAccount.setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }

        val btnTeachers = findViewById<MaterialButton>(R.id.btnTeachers)
        btnTeachers.setOnClickListener {
            startActivity(Intent(this, TeachersListActivity::class.java))
        }

        val tvName = findViewById<TextView>(R.id.tvProfileName)
        val tvEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val tvPackagesHeader = findViewById<TextView>(R.id.tvPackagesHeader)
        val rvPackages = findViewById<RecyclerView>(R.id.rvPackages)
        val rvUpcoming = findViewById<RecyclerView>(R.id.rvUpcomingLessons)
        val rvCompleted = findViewById<RecyclerView>(R.id.rvCompletedLessons)
        val rvUnscheduled = findViewById<RecyclerView>(R.id.rvUnscheduledLessons)
        
        val layoutPackages = findViewById<LinearLayout>(R.id.layoutPackagesHeader)
        val layoutUpcoming = findViewById<LinearLayout>(R.id.layoutUpcomingHeader)
        val layoutCompleted = findViewById<LinearLayout>(R.id.layoutCompletedHeader)
        val layoutUnscheduled = findViewById<LinearLayout>(R.id.layoutUnscheduledHeader)
        
        val ivPackagesArrow = findViewById<ImageView>(R.id.ivPackagesArrow)
        val ivUpcomingArrow = findViewById<ImageView>(R.id.ivUpcomingArrow)
        val ivCompletedArrow = findViewById<ImageView>(R.id.ivCompletedArrow)
        val ivUnscheduledArrow = findViewById<ImageView>(R.id.ivUnscheduledArrow)

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshProfile)
        val progressBar = findViewById<ProgressBar>(R.id.profileProgressBar)
        val btnBuyPackage = findViewById<MaterialButton>(R.id.btnBuyPackage)

        rvPackages.layoutManager = LinearLayoutManager(this)
        rvUpcoming.layoutManager = LinearLayoutManager(this)
        rvCompleted.layoutManager = LinearLayoutManager(this)
        rvUnscheduled.layoutManager = LinearLayoutManager(this)

        layoutPackages.tag = "collapsed"
        layoutUpcoming.tag = "collapsed"
        layoutCompleted.tag = "collapsed"
        layoutUnscheduled.tag = "collapsed"

        rvPackages.visibility = View.GONE
        rvUpcoming.visibility = View.GONE
        rvCompleted.visibility = View.GONE
        rvUnscheduled.visibility = View.GONE

        ivPackagesArrow.setImageResource(android.R.drawable.arrow_down_float)
        ivUpcomingArrow.setImageResource(android.R.drawable.arrow_down_float)
        ivCompletedArrow.setImageResource(android.R.drawable.arrow_down_float)
        ivUnscheduledArrow.setImageResource(android.R.drawable.arrow_down_float)

        btnBuyPackage.setOnClickListener {
            startActivity(Intent(this, BookingActivity::class.java))
        }

        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        setupCollapsible(layoutPackages, rvPackages, ivPackagesArrow)
        setupCollapsible(layoutUpcoming, rvUpcoming, ivUpcomingArrow)
        setupCollapsible(layoutCompleted, rvCompleted, ivCompletedArrow)
        setupCollapsible(layoutUnscheduled, rvUnscheduled, ivUnscheduledArrow)

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadProfileData()
        }

        viewModel.profile.observe(this) { profile ->
            profile?.let {
                tvName.text = it.full_name ?: "User"
                tvEmail.text = it.email ?: ""
                
                if (it.role == "teacher") {
                    tvPackagesHeader.text = "booked packages"
                    btnBuyPackage.visibility = View.GONE 
                } else {
                    tvPackagesHeader.text = "My Lesson Packages"
                    btnBuyPackage.visibility = View.VISIBLE
                }
            }
        }

        viewModel.activePackages.observe(this) { packages ->
            layoutPackages.visibility = View.VISIBLE 
            rvPackages.visibility = if (packages.isEmpty() || layoutPackages.tag == "collapsed") View.GONE else View.VISIBLE
            
            packageAdapter = PackageAdapter(packages) { selectedPackage ->
                val intent = Intent(this, PackageDetailsActivity::class.java).apply {
                    putExtra("BOOKING_ID", selectedPackage.id)
                    putExtra("SERVICE_NAME", selectedPackage.service?.course_name ?: "Lesson Package")
                    putExtra("REMAINING_LESSONS", selectedPackage.remaining_lessons)
                    putExtra("BOOKITIT_SERVICE_ID", selectedPackage.service?.bookitit_service_id)
                    putExtra("BOOKITIT_AGENDA_ID", selectedPackage.bookitit_agenda_id ?: selectedPackage.service?.bookitit_agenda_id)
                    putExtra("SERVICE_DURATION", selectedPackage.service?.duration ?: 30) 
                    putExtra("EXPIRES_AT", selectedPackage.expires_at)
                    putExtra("USER_ROLE", viewModel.profile.value?.role)
                }
                startActivity(intent)
            }
            rvPackages.adapter = packageAdapter
        }

        viewModel.upcomingLessons.observe(this) { lessons ->
            layoutUpcoming.visibility = View.VISIBLE 
            rvUpcoming.visibility = if (lessons.isEmpty() || layoutUpcoming.tag == "collapsed") View.GONE else View.VISIBLE
            
            upcomingLessonAdapter = LessonAdapter(lessons) { lessonToUnschedule ->
                viewModel.requestUnschedule(lessonToUnschedule)
            }
            rvUpcoming.adapter = upcomingLessonAdapter
        }

        viewModel.completedLessons.observe(this) { lessons ->
            layoutCompleted.visibility = View.VISIBLE 
            rvCompleted.visibility = if (lessons.isEmpty() || layoutCompleted.tag == "collapsed") View.GONE else View.VISIBLE
            
            completedLessonAdapter = LessonAdapter(lessons) { }
            rvCompleted.adapter = completedLessonAdapter
        }

        viewModel.unscheduledLessons.observe(this) { lessons ->
            layoutUnscheduled.visibility = View.VISIBLE 
            rvUnscheduled.visibility = if (lessons.isEmpty() || layoutUnscheduled.tag == "collapsed") View.GONE else View.VISIBLE
            
            unscheduledLessonAdapter = LessonAdapter(lessons) { }
            rvUnscheduled.adapter = unscheduledLessonAdapter
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (swipeRefreshLayout.isRefreshing) {
                if (!isLoading) swipeRefreshLayout.isRefreshing = false
            } else {
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        viewModel.unscheduleMessage.observe(this) { message ->
            message?.let {
                val dialog = AlertDialog.Builder(this)
                    .setTitle("Success")
                    .setMessage(it)
                    .setPositiveButton("OK", null)
                    .create()
                dialog.show()
                dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
                viewModel.clearUnscheduleMessage()
            }
        }

        viewModel.showUnscheduleWarning.observe(this) { show ->
            if (show) showUnscheduleWarningDialog()
        }

        viewModel.showReasonInput.observe(this) { lesson ->
            lesson?.let { showReasonInputDialog(it) }
        }

        viewModel.loadProfileData()
    }

    private fun showUnscheduleWarningDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_unschedule_warning, null)
        val tvMessage = view.findViewById<TextView>(R.id.tvWarningMessage)
        
        val message = "Lessons must be unscheduled at least 20 hours in advance. If you will not attend the lesson either way, please make sure to send a notice on the teacher's email. For more details, read the terms of service."
        val spannable = SpannableString(message)
        val linkText = "terms of service"
        val startIndex = message.indexOf(linkText)
        val endIndex = startIndex + linkText.length
        
        if (startIndex != -1) {
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openUrl(TERMS_URL)
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = Color.BLUE
                    ds.isUnderlineText = true
                    ds.isFakeBoldText = true
                }
            }
            spannable.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        tvMessage.text = spannable
        tvMessage.movementMethod = LinkMovementMethod.getInstance()

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<MaterialButton>(R.id.btnUnderstand).setOnClickListener {
            viewModel.clearUnscheduleWarning()
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showReasonInputDialog(lesson: BookititEvent) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_unschedule_reason, null)
        val etReason = view.findViewById<TextInputEditText>(R.id.etReason)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirmUnschedule)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setOnDismissListener { viewModel.clearReasonInput() }
            .create()

        etReason.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnConfirm.isEnabled = (s?.length ?: 0) >= 5
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnConfirm.setOnClickListener {
            val reason = etReason.text.toString()
            viewModel.performUnschedule(lesson, reason)
            viewModel.clearReasonInput()
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun setupCollapsible(header: View, content: View, arrow: ImageView) {
        header.setOnClickListener {
            if (content.visibility == View.VISIBLE) {
                content.visibility = View.GONE
                arrow.setImageResource(android.R.drawable.arrow_down_float)
                header.tag = "collapsed"
            } else {
                content.visibility = View.VISIBLE
                arrow.setImageResource(android.R.drawable.arrow_up_float)
                header.tag = "expanded"
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logoutUser() {
        lifecycleScope.launch {
            try {
                SupabaseClientObj.client.auth.signOut()
                Toast.makeText(this@ProfileActivity, "Logged out", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Logout failed: ${e.message}")
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.loadProfileData()
    }
}
