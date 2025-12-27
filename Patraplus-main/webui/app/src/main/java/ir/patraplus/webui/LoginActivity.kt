package ir.patraplus.webui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_login)

        val emailInput = findViewById<TextInputEditText>(R.id.loginEmail)
        val passwordInput = findViewById<TextInputEditText>(R.id.loginPassword)
        val loginButton = findViewById<MaterialButton>(R.id.loginButton)

        passwordInput.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val container = findViewById<android.view.View>(R.id.loginContainer)
        container.alpha = 0f
        container.translationY = 40f
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        loginButton.setOnClickListener {
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString()?.trim().orEmpty()

            if (email == ALLOWED_EMAIL && password == ALLOWED_PASSWORD) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "ایمیل یا رمز عبور اشتباه است.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val ALLOWED_EMAIL = "patraplus@gmail.com"
        private const val ALLOWED_PASSWORD = "@123456@"
    }
}
