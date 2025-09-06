package com.ollamaserver

import android.os.Environment
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.MutableLiveData

object OllamaDataRepository {
    // Datos del servicio
    val terminalOutput = MutableLiveData<String>()
    val serviceStatus = MutableLiveData<String>()
    val isServiceRunning = MutableLiveData<Boolean>(false)
    
    // Estadísticas del servicio
    val serverStats = MutableLiveData<ServerStats>()
    
    // Comandos para enviar al servicio
    private val _commandQueue = MutableLiveData<String?>()
    val commandQueue: MutableLiveData<String?> get() = _commandQueue

    // Para comunicación con el servicio
    var serviceIntent: Intent? = null
    
    data class ServerStats(
        val ramUsage: String = "--",
        val cpuUsage: String = "--",
        val swapUsage: String = "--",
        val tokensGenerated: String = "--",
        val currentModel: String = "--",
        val activeConnections: Int = 0
    )

    init {
        // Inicializar con valores por defecto
        terminalOutput.value = "Terminal inicializado\n"
        serviceStatus.value = "Servicio no iniciado"
        serverStats.value = ServerStats()
        _commandQueue.value = null
    }
    
    fun appendOutput(output: String) {
        val current = terminalOutput.value ?: ""
        terminalOutput.value = current + output + "\n"
    }
    
    fun updateStatus(status: String) {
        serviceStatus.value = status
    }
    
    fun setServiceRunning(running: Boolean) {
        isServiceRunning.value = running
    }
    
    fun updateStats(stats: ServerStats) {
        serverStats.value = stats
    }
    
    fun sendCommand(command: String) {
        _commandQueue.value = command
        // También enviar el comando al servicio si está disponible
        serviceIntent?.let { intent ->
            intent.action = "SEND_COMMAND"
            intent.putExtra("COMMAND", command)
        }
    }
    
    fun clearTerminal() {
        terminalOutput.value = ""
    }
    
    fun getTerminalHistory(): List<String> {
        return terminalOutput.value?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }
    
    // Métodos para manejar permisos de almacenamiento en Android 11+
    fun hasAllFilesAccessPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Settings.canDrawOverlays(context) // Usamos esto como proxy para verificar permisos
            // Nota: Para MANAGE_EXTERNAL_STORAGE se necesita una verificación específica
        } else {
            true // En versiones anteriores, asumimos que tiene permisos
        }
    }
    
    fun requestAllFilesAccessPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback si no está disponible la acción específica
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
    
    // Método para verificar si se concedió el permiso MANAGE_EXTERNAL_STORAGE
    fun hasManageExternalStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isManageExternalStorageAllowed()
        } else {
            true // En versiones anteriores no se necesita este permiso
        }
    }
    
    // Método para comunicación bidireccional con el servicio
    fun setServiceIntent(intent: Intent) {
        serviceIntent = intent
    }
    
    // Método para limpiar el comando después de procesarlo
    fun clearCommand() {
        _commandQueue.value = null
    }
}
