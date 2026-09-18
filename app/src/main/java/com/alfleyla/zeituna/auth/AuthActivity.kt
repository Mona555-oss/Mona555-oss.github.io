package com.alfleyla.zeituna.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.alfleyla.zeituna.R

class AuthActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // Initialize the ViewModel
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        // Find the registration button and set click listener
        val btnNavigateRegister = findViewById<Button>(R.id.btnNavigateRegister)
        btnNavigateRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}