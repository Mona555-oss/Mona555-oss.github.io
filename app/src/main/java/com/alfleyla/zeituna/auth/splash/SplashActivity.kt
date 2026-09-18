package com.alfleyla.zeituna.auth.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.alfleyla.zeituna.auth.LoginActivity
import com.alfleyla.zeituna.data.SupabaseClientObj
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkSession()
    }

    private fun checkSession() {
        lifecycleScope.launch {
            SupabaseClientObj.client.auth.awaitInitialization()

            // Start LoginActivity instead of AuthActivity
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            finish()
        }
    }
}