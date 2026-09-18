package com.alfleyla.zeituna.auth

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModelProvider
import com.alfleyla.zeituna.R

class RegisterActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel

    private val PRIVACY_URL = "https://mona555-oss.github.io/privacy_policy.html"
    private val TERMS_URL = "https://mona555-oss.github.io/terms_of_service.html"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        val cbPrivacy = findViewById<CheckBox>(R.id.cbPrivacy)
        val cbTerms = findViewById<CheckBox>(R.id.cbTerms)
        val tvPrivacyLink = findViewById<TextView>(R.id.tvPrivacyLink)
        val tvTermsLink = findViewById<TextView>(R.id.tvTermsLink)

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnNavigateLogin = findViewById<Button>(R.id.btnNavigateLogin)
        
        val registrationForm = findViewById<ScrollView>(R.id.registrationForm)
        val layoutSuccess = findViewById<LinearLayout>(R.id.layoutSuccess)
        val btnSuccessLogin = findViewById<Button>(R.id.btnSuccessLogin)

        tvPrivacyLink.setOnClickListener { openUrl(PRIVACY_URL) }
        tvTermsLink.setOnClickListener { openUrl(TERMS_URL) }

        btnRegister.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            
            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showSimpleDialog("Error", "Please fill in all fields")
                return@setOnClickListener
            }

            // Password restriction check: length >= 6, at least one uppercase, at least one number
            val hasDigit = password.any { it.isDigit() }
            val hasUpper = password.any { it.isUpperCase() }
            if (password.length < 6 || !hasDigit || !hasUpper) {
                showPasswordInstructionsDialog()
                return@setOnClickListener
            }

            if (!cbPrivacy.isChecked) {
                showSimpleDialog("Notice", "You must agree to the Privacy Policy")
                return@setOnClickListener
            }
            if (!cbTerms.isChecked) {
                showSimpleDialog("Notice", "You must agree to the Terms of Service")
                return@setOnClickListener
            }

            viewModel.registerStudent(email, password, fullName)
        }

        viewModel.registrationStatus.observe(this) { result ->
            result.onSuccess {
                registrationForm.visibility = View.GONE
                layoutSuccess.visibility = View.VISIBLE
            }.onFailure { e ->
                val errorMsg = e.message ?: "Unknown error occurred"
                // If Supabase returns a password error, show the instructions dialog
                if (errorMsg.contains("password", ignoreCase = true)) {
                    showPasswordInstructionsDialog()
                } else {
                    showSimpleDialog("Registration Failed", errorMsg)
                }
            }
        }

        btnNavigateLogin.setOnClickListener { finish() }
        btnSuccessLogin.setOnClickListener { finish() }
    }

    private fun showPasswordInstructionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Password Requirements")
            .setMessage("Password must be at least 6 characters long and contain at least one uppercase letter and one number.")
            .setPositiveButton("OK", null)
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
}
