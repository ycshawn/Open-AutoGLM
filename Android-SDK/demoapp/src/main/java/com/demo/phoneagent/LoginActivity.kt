package com.demo.phoneagent

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.demo.phoneagent.databinding.ActivityLoginBinding

/**
 * Login activity for testing the agent.
 * This page can be automated by the agent.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Login button
        binding.btnLogin.setOnClickListener {
            handleLogin()
        }

        // Forgot password
        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "忘记密码功能", Toast.LENGTH_SHORT).show()
        }

        // Register
        binding.tvRegister.setOnClickListener {
            Toast.makeText(this, "注册功能", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLogin() {
        val username = binding.etUsername.text?.toString()?.trim()
        val password = binding.etPassword.text?.toString()?.trim()

        when {
            username.isNullOrEmpty() -> {
                binding.tilUsername.error = "请输入用户名"
                Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
            }
            password.isNullOrEmpty() -> {
                binding.tilPassword.error = "请输入密码"
                Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
            }
            username == "admin" && password == "123456" -> {
                binding.tilUsername.error = null
                binding.tilPassword.error = null
                Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                // In a real app, navigate to the next screen
                finish()
            }
            else -> {
                binding.tilUsername.error = null
                binding.tilPassword.error = null
                Toast.makeText(this, getString(R.string.login_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    // For testing - allow programmatic login
    fun setUsername(username: String) {
        binding.etUsername.setText(username)
    }

    fun setPassword(password: String) {
        binding.etPassword.setText(password)
    }

    fun clickLogin() {
        binding.btnLogin.performClick()
    }
}
