package com.alfleyla.zeituna.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.auth.LoginActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AccountActivity : AppCompatActivity() {

    private lateinit var viewModel: ProfileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarAccount)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        val tvName = findViewById<TextView>(R.id.tvAccountName)
        val tvEmail = findViewById<TextView>(R.id.tvAccountEmail)
        val tvMemberSince = findViewById<TextView>(R.id.tvAccountMemberSince)
        val btnDelete = findViewById<MaterialButton>(R.id.btnDeleteAccount)
        val progressBar = findViewById<ProgressBar>(R.id.progressBarAccount)

        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        viewModel.profile.observe(this) { profile ->
            profile?.let {
                tvName.text = it.full_name ?: "User"
                tvEmail.text = it.email ?: ""
                tvMemberSince.text = formatMemberDate(it.created_at)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnDelete.isEnabled = !isLoading
        }

        viewModel.deleteStatus.observe(this) { result ->
            result?.let {
                it.onSuccess {
                    showDeletionRequestReceivedDialog()
                }.onFailure { e ->
                    Toast.makeText(this, "Error deleting account: ${e.message}", Toast.LENGTH_LONG).show()
                }
                viewModel.clearDeleteStatus()
            }
        }

        btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }

        viewModel.loadProfileData()
    }

    private fun showDeletionRequestReceivedDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Request Received")
            .setMessage("Your account deletion request has been received. Your account will be permanently deleted within 3 business days.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                navigateToLogin()
            }
            .create()
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showDeleteConfirmation() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone and all your booking history will be lost.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun formatMemberDate(createdAt: String?): String {
        if (createdAt.isNullOrEmpty()) return "-"
        return try {
            val normalized = createdAt.replace(" ", "T").substringBefore("+").substringBefore("Z")
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(normalized)
            if (date != null) outputFormat.format(date) else createdAt.take(10)
        } catch (e: Exception) {
            createdAt.take(10)
        }
    }
}
