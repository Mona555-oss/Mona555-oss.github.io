package com.alfleyla.zeituna.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alfleyla.zeituna.auth.LoginActivity
import com.alfleyla.zeituna.theme.ZeitunaTheme
import com.alfleyla.zeituna.ui.dashboard.TeacherDashboardScreen

class TeacherDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ZeitunaTheme {
                val viewModel: ProfileViewModel = viewModel { ProfileViewModel() }

                TeacherDashboardScreen(
                    viewModel = viewModel,
                    onLogout = {
                        val intent = Intent(this@TeacherDashboardActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    },
                    onPackageClick = { selectedPackage ->
                        val intent = Intent(this@TeacherDashboardActivity, PackageDetailsActivity::class.java).apply {
                            putExtra("BOOKING_ID", selectedPackage.id)
                            putExtra("SERVICE_NAME", selectedPackage.service?.course_name ?: "Lesson Package")
                            putExtra("REMAINING_LESSONS", selectedPackage.remaining_lessons)
                            putExtra("BOOKITIT_SERVICE_ID", selectedPackage.service?.bookitit_service_id)
                            putExtra("BOOKITIT_AGENDA_ID", selectedPackage.bookitit_agenda_id ?: selectedPackage.service?.bookitit_agenda_id)
                            putExtra("SERVICE_DURATION", selectedPackage.service?.duration ?: 30)
                            putExtra("EXPIRES_AT", selectedPackage.expires_at)
                            putExtra("USER_ROLE", "teacher")
                            putExtra("STUDENT_NAME", selectedPackage.student_name)
                            putExtra("STUDENT_EMAIL", selectedPackage.email)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
