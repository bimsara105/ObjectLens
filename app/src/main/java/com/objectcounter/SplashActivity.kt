package com.objectcounter

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val progressBar = findViewById<ProgressBar>(R.id.splash_progress)
        val statusText  = findViewById<TextView>(R.id.splash_status)

        lifecycleScope.launch {
            if (!ModelDownloader.isModelDownloaded(this@SplashActivity)) {
                progressBar.visibility = View.VISIBLE
                statusText.text = "YOLOv8 model download කරනවා..."

                val success = ModelDownloader.downloadModel(this@SplashActivity) { progress ->
                    progressBar.progress = progress
                    statusText.text = "Downloading... $progress%"
                }

                if (!success) {
                    statusText.text = "Download failed. Internet connection check කරන්න."
                    return@launch
                }
            }

            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}
