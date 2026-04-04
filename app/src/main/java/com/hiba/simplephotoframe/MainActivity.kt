package com.hiba.simplephotoframe

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val settings = SettingsManager(newBase).getSettings()
        super.attachBaseContext(LocaleHelper.setLocale(newBase, settings.language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setMessage(getString(R.string.about_message))
            .setPositiveButton("OK", null)
            .show()
    }
}
