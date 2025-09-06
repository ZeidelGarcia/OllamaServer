package com.ollamaserver

import android.app.Notification
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.commit
import androidx.lifecycle.Observer

class MainActivity : AppCompatActivity() {

    private var ollamaRootUri: Uri? = null
    private val viewModel: OllamaViewModel by viewModels()

    private val PREFS_NAME = "OllamaPrefs"
    private val KEY_OLLAMA_URI = "ollama_root_uri"

    // Selector de carpeta raíz
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                try {
                    // Guardar permiso persistente
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    ollamaRootUri = uri
                    saveOllamaUri(uri)

                    val folder = DocumentFile.fromTreeUri(this, uri)
                    if (folder != null && folder.canRead()) {
                        startOllamaService()
                        showInfoDialog(folder.uri.toString())
                        loadTerminalFragment()
                    } else {
                        Toast.makeText(this, "Error al acceder a la carpeta ❌", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al guardar permisos: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(this, "No seleccionaste ninguna carpeta ⚠️", Toast.LENGTH_SHORT).show()
        }
    }

    // Permisos de notificación
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Las notificaciones son necesarias para el servicio en primer plano", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bloquear rotación (solo vertical)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Crear canal de notificaciones (debe hacerse antes de verificar permisos)
        createNotificationChannel()

        // Verificar permisos de notificación
        checkNotificationPermission()

        // Observar cambios en el estado del servicio
        viewModel.serviceStatus.observe(this, Observer { status ->
            status?.let {
                // Aquí puedes actualizar la UI con el estado del servicio
                Toast.makeText(this, "Estado del servicio: $it", Toast.LENGTH_SHORT).show()
            }
        })

        // Cargar carpeta guardada si existe
        val savedUri = loadOllamaUri()
        if (savedUri != null) {
            try {
                contentResolver.query(savedUri, null, null, null, null)?.close()
                ollamaRootUri = savedUri
                startOllamaService()
                showInfoDialog(savedUri.toString())
                loadTerminalFragment()
            } catch (e: Exception) {
                showOllamaFolderDialog()
            }
        } else {
            showOllamaFolderDialog()
        }
    }

    // Función para conectar con TerminalFragment usando ViewModel
    private fun connectTerminalToService() {
        val fragment = supportFragmentManager.findFragmentById(R.id.terminalContainer)
        if (fragment is TerminalFragment) {
            // El fragmento ya tiene acceso al ViewModel compartido
            viewModel.updateServiceStatus("Servicio conectado al terminal")
            Toast.makeText(this, "Terminal conectado al servicio ✅", Toast.LENGTH_SHORT).show()
        } else {
            // Intentar de nuevo después de un breve delay
            Handler(Looper.getMainLooper()).postDelayed({
                connectTerminalToService()
            }, 100)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, 
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "ollama_channel"
            val channelName = "Ollama Service"
            val importance = NotificationManager.IMPORTANCE_LOW
            
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Canal para las notificaciones del servicio Ollama"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Dialogo inicial para elegir carpeta
    private fun showOllamaFolderDialog() {
        AlertDialog.Builder(this)
            .setTitle("Seleccionar carpeta raíz de Ollama")
            .setMessage("Por favor selecciona la carpeta donde está instalado Ollama para que la app funcione correctamente.")
            .setCancelable(false)
            .setPositiveButton("Seleccionar") { _, _ ->
                openFolderPicker()
            }
            .setNegativeButton("Salir") { _, _ ->
                finish()
            }
            .show()
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        folderPickerLauncher.launch(intent)
    }

    // Guardar URI en SharedPreferences
    private fun saveOllamaUri(uri: Uri) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_OLLAMA_URI, uri.toString()).apply()
    }

    // Cargar URI guardada
    private fun loadOllamaUri(): Uri? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_OLLAMA_URI, null)
        return uriString?.let { Uri.parse(it) }
    }

    // Arrancar el servicio de Ollama
    private fun startOllamaService() {
        val intent = Intent(this, OllamaService::class.java).apply { 
            action = "START"
        }
        
        // Configurar el intent del servicio en el Repository para comunicación
        OllamaDataRepository.setServiceIntent(intent)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // Mostrar popup de servidor iniciado
    private fun showInfoDialog(dir: String) {
        AlertDialog.Builder(this)
            .setTitle("Servidor iniciado")
            .setMessage("Ollama corriendo en http://localhost:11434\nCarpeta de modelos: $dir")
            .setPositiveButton("OK") { _, _ ->
                loadTerminalFragment()
                // Conectar después de un pequeño delay para asegurar que el fragmento esté creado
                Handler(Looper.getMainLooper()).postDelayed({
                    connectTerminalToService()
                }, 300)
            }
            .setCancelable(false)
            .show()
    }

    // Cargar fragmento de terminal
    private fun loadTerminalFragment() {
        supportFragmentManager.commit {
            replace(R.id.terminalContainer, TerminalFragment())
        }
    }

    // Extra: acceso total a archivos Android 11+
    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detener servicio cuando la actividad se destruye
        val stopIntent = Intent(this, OllamaService::class.java).apply {
            action = "STOP"
        }
        startService(stopIntent)
    }
}
