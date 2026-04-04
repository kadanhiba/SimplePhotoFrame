package com.example.simplephotoframe

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkBatteryOptimizations()

        findViewById<Button>(R.id.btnSelectFolders).setOnClickListener {
            startActivity(Intent(this, FoldersActivity::class.java))
        }

        findViewById<Button>(R.id.btnSlideshowSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnStartSlideshow).setOnClickListener {
            startActivity(Intent(this, SlideshowActivity::class.java))
        }

        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        val message = """
            Simple Photo Frame v1.0
            
            This app is designed to turn your tablet or phone into a dedicated digital photo frame. It supports looping slideshows of photos and videos from your local folders with customizable transitions, clock overlays, and power management.
            
            Disclaimer:
            This app is provided "as-is" for free. Use of this application is at the user's own responsibility. The author holds no commitment to reliability, fitness for a particular purpose, or responsibility for any impact (hardware or software) resulting from its use.
            
            Contact: the.hiba.family@gmail.com
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("About Simple Photo Frame")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Please disable battery optimization in settings to ensure the slideshow runs continuously.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
