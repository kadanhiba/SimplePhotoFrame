package com.hiba.simplephotoframe

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class FoldersActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var folders: MutableList<String>
    private lateinit var adapter: ArrayAdapter<String>

    override fun attachBaseContext(newBase: Context) {
        val settings = SettingsManager(newBase).getSettings()
        super.attachBaseContext(LocaleHelper.setLocale(newBase, settings.language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folders)

        settingsManager = SettingsManager(this)
        folders = settingsManager.getFolders()

        val lvFolders = findViewById<ListView>(R.id.lvFolders)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, folders)
        lvFolders.adapter = adapter

        findViewById<Button>(R.id.btnAddFolder).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, 100)
        }

        findViewById<Button>(R.id.btnBackFolders).setOnClickListener {
            finish()
        }

        lvFolders.setOnItemLongClickListener { _, _, position, _ ->
            folders.removeAt(position)
            adapter.notifyDataSetChanged()
            settingsManager.saveFolders(folders)
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                val uriString = uri.toString()
                if (!folders.contains(uriString)) {
                    folders.add(uriString)
                    adapter.notifyDataSetChanged()
                    settingsManager.saveFolders(folders)
                }
            }
        }
    }
}
