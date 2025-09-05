package com.ollamaserver

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.commit

class MainActivity : AppCompatActivity() {

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // Permisos concedidos, ahora lanzar el selector de carpeta
            launchFolderPicker()
        } else {
            // Permisos denegados
            Toast.makeText(this, "Se necesitan permisos de almacenamiento para funcionar", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            // Tomar control persistente de la URI
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            
            val file = DocumentFile.fromTreeUri(this, uri)
            file?.findFile(".ollama") ?: file?.createDirectory(".ollama")
            val modelPath = file?.uri.toString() + "/.ollama"
            getSharedPreferences("config", MODE_PRIVATE).edit()
                .putString("ollama_dir", modelPath)
                .apply()
            startOllamaService()
            showInfoDialog(modelPath)
        } ?: run {
            // Usuario canceló la selección
            Toast.makeText(this, "Debes seleccionar una carpeta para continuar", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Primero verificar y solicitar permisos básicos
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Verificar permisos básicos de almacenamiento
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissionsToRequest.isNotEmpty()) {
            // Solicitar permisos básicos primero
            storagePermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Ya tiene permisos, verificar si ya seleccionó carpeta
            checkExistingFolder()
        }
    }

    private fun checkExistingFolder() {
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val dir = prefs.getString("ollama_dir", null)
        if (dir == null) {
            launchFolderPicker()
        } else {
            // Verificar que aún tenemos acceso a la URI
            try {
                Uri.parse(dir)?.let { uri ->
                    contentResolver.query(uri, null, null, null, null)?.close()
                }
                startOllamaService()
                showInfoDialog(dir)
                loadTerminalFragment()
            } catch (e: Exception) {
                // Perdimos acceso, pedir nueva carpeta
                getSharedPreferences("config", MODE_PRIVATE).edit().remove("ollama_dir").apply()
                launchFolderPicker()
            }
        }
    }

    private fun launchFolderPicker() {
        // Lanzar el selector de árbol de documentos de SAF
        folderPicker.launch(null)
    }

    private fun startOllamaService() {
        val intent = Intent(this, OllamaService::class.java).apply { action = "START" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showInfoDialog(dir: String) {
        AlertDialog.Builder(this)
            .setTitle("Servidor iniciado")
            .setMessage("Ollama corriendo en http://localhost:11434\nCarpeta de modelos: $dir")
            .setPositiveButton("OK") { _, _ -> 
                loadTerminalFragment()
            }
            .setCancelable(false)
            .show()
    }

    private fun loadTerminalFragment() {
        supportFragmentManager.commit {
            replace(R.id.terminalContainer, TerminalFragment())
        }
    }

    // Para Android 11+ si necesitas acceso completo (opcional)
    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }
}
