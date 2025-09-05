package com.ollamaserver

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.documentfile.provider.DocumentFile

class MainActivity : AppCompatActivity() {

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val file = DocumentFile.fromTreeUri(this, uri)
            file?.findFile(".ollama") ?: file?.createDirectory(".ollama")
            val modelPath = file?.uri.toString() + "/.ollama"
            getSharedPreferences("config", MODE_PRIVATE).edit()
                .putString("ollama_dir", modelPath)
                .apply()
            startOllamaService()
            showInfoDialog(modelPath)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val dir = prefs.getString("ollama_dir", null)
        if (dir == null) folderPicker.launch(null)
        else {
            startOllamaService()
            showInfoDialog(dir)
        }

        supportFragmentManager.commit {
            replace(R.id.terminalContainer, TerminalFragment())
        }
    }

    private fun startOllamaService() {
        val intent = Intent(this, OllamaService::class.java).apply { action = "START" }
        startForegroundService(intent)
    }

    private fun showInfoDialog(dir: String) {
        AlertDialog.Builder(this)
            .setTitle("Servidor iniciado")
            .setMessage("Ollama corriendo en http://localhost:11434\nCarpeta de modelos: $dir")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
