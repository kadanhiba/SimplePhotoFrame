package com.hiba.simplephotoframe

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class FoldersActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var foldersList: MutableList<String>
    private lateinit var adapter: ArrayAdapter<String>

    private val openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(it, takeFlags)
            
            val folderPath = it.toString()
            if (!foldersList.contains(folderPath)) {
                foldersList.add(folderPath)
                settingsManager.saveFolders(foldersList)
                adapter.notifyDataSetChanged()
            } else {
                Toast.makeText(this, "Folder already added", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folders)

        settingsManager = SettingsManager(this)
        foldersList = settingsManager.getFolders()

        val listView = findViewById<ListView>(R.id.lvFolders)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, foldersList)
        listView.adapter = adapter

        findViewById<Button>(R.id.btnAddFolder).setOnClickListener {
            openDocumentTreeLauncher.launch(null)
        }

        findViewById<Button>(R.id.btnBackFolders).setOnClickListener {
            finish()
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            showDeleteDialog(position)
            true
        }
    }

    private fun showDeleteDialog(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Remove Folder")
            .setMessage("Do you want to remove this folder from the list?")
            .setPositiveButton("Remove") { _, _ ->
                foldersList.removeAt(position)
                settingsManager.saveFolders(foldersList)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
