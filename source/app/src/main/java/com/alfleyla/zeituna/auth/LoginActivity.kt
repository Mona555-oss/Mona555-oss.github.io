package com.alfleyla.zeituna.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModelProvider
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.data.models.Profile
import com.alfleyla.zeituna.profile.ProfileActivity
import com.alfleyla.zeituna.profile.TeacherDashboardActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel
    private lateinit var googleSignInClient: GoogleSignInClient

    private val PRIVACY_URL = "https://mona555-oss.github.io/privacy_policy.html"
    private val TERMS_URL = "https://mona555-oss.github.io/terms_of_service.html"

    // TODO: MUST REPLACE THIS with your actual Web Client ID from Google Cloud Console
    private val WEB_CLIENT_ID = "909908994444-vptsd95e8kceuet7hfk1qurtg346r6p5.apps.googleusercontent.com"

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                viewModel.loginWithGoogle(idToken) { profile ->
                    if (profile != null) {
                        handlePostLogin(profile)
                    } else {
                        showSimpleDialog("Authentication Failed", "Google Sign-In failed on the server. Please try again.")
                    }
                }
            }
        } catch (e: ApiException) {
            Log.e("AuthFlow", "Google sign in failed. Status Code: ${e.statusCode}", e)
            showSimpleDialog("Login Error", "Google Sign-In failed (Status ${e.statusCode}). Please check your connection or configuration.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        val etEmail = findViewById<EditText>(R.id.etLoginEmail)
        val etPassword = findViewById<EditText>(R.id.etLoginPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogleLogin = findViewById<Button>(R.id.btnGoogleLogin)
        val btnNavigateRegister = findViewById<Button>(R.id.btnNavigateRegister)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                viewModel.loginUser(email, password) { profile ->
                    if (profile != null) {
                        handlePostLogin(profile)
                    } else {
                        showSimpleDialog("Login Failed", "Invalid email or password. Please check your credentials.")
                    }
                }
            } else {
                showSimpleDialog("Missing Information", "Please enter both your email and password.")
            }
        }

        btnGoogleLogin.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }

        btnNavigateRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                showSimpleDialog("Email Required", "Please enter your email in the field above to receive a reset link.")
            } else {
                showResetPasswordConfirmation(email)
            }
        }

        viewModel.resetPasswordStatus.observe(this) { result ->
            result?.let {
                it.onSuccess {
                    showSimpleDialog("Success", "A password reset link has been sent to your email. Please check your inbox.")
                }.onFailure { e ->
                    showSimpleDialog("Error", "Failed to send reset email: ${e.message}")
                }
                viewModel.clearResetStatus()
            }
        }
    }

    private fun handlePostLogin(profile: Profile) {
        if (!profile.agreed_to_legal) {
            showMandatoryLegalDialog(profile)
        } else {
            navigateToDashboard(profile.role)
        }
    }

    private fun showMandatoryLegalDialog(profile: Profile) {
        AlertDialog.Builder(this)
            .setTitle("Legal Agreement")
            .setMessage("Welcome! To continue, please agree to our Privacy Policy and Terms of Service.")
            .setPositiveButton("I Agree") { _, _ ->
                viewModel.updateLegalAgreement(profile.id)
                navigateToDashboard(profile.role)
            }
            .setNegativeButton("View Privacy") { _, _ -> openUrl(PRIVACY_URL); showMandatoryLegalDialog(profile) }
            .setNeutralButton("View Terms") { _, _ -> openUrl(TERMS_URL); showMandatoryLegalDialog(profile) }
            .setCancelable(false)
            .show()
    }

    private fun showSimpleDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResetPasswordConfirmation(email: String) {
        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("Send a password reset link to $email?")
            .setPositiveButton("Send") { _, _ ->
                val myRedirectUrl = "https://mona555-oss.github.io/reset-password.html"
                viewModel.sendResetPasswordEmail(email, myRedirectUrl)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToDashboard(role: String?) {
        val intent = if (role == "teacher") {
            Intent(this, TeacherDashboardActivity::class.java)
        } else {
            Intent(this, ProfileActivity::class.java)
        }
        startActivity(intent)
        finishAffinity()
    }
}
