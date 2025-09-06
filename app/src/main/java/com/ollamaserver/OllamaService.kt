package com.ollamaserver

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.OutputStreamWriter

class OllamaService : Service() {

    private var process: Process? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startOllama()
            "STOP" -> stopOllama()
            "RESTART" -> restartOllama()
            "SEND_COMMAND" -> {
                val command = intent.getStringExtra("COMMAND")
                command?.let { sendCommandToProcess(it) }
            }
        }
        return START_STICKY
    }

    private fun startOllama() {
        if (isRunning) {
            OllamaDataRepository.appendOutput("El servicio ya está ejecutándose")
            return
        }

        OllamaDataRepository.updateStatus("Iniciando servicio Ollama...")
        OllamaDataRepository.setServiceRunning(true)
        OllamaDataRepository.appendOutput("=== INICIANDO SERVIDOR OLLAMA ===")

        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val modelDir = prefs.getString("ollama_dir", filesDir.absolutePath + "/.ollama") ?: run {
            OllamaDataRepository.appendOutput("ERROR: No se encontró directorio de modelos")
            OllamaDataRepository.updateStatus("Error: Directorio no configurado")
            return
        }

        // Preparar binario
        val binFile = File(filesDir, "bin/ollama")
        if (!binFile.exists()) {
            OllamaDataRepository.appendOutput("Copiando binario Ollama...")
            try {
                assets.open("bin/ollama_aarch64").use { input ->
                    binFile.outputStream().use { output -> input.copyTo(output) }
                }
                binFile.setExecutable(true)
                OllamaDataRepository.appendOutput("Binario copiado y hecho ejecutable")
            } catch (e: Exception) {
                OllamaDataRepository.appendOutput("ERROR al copiar binario: ${e.message}")
                OllamaDataRepository.updateStatus("Error: ${e.message}")
                return
            }
        }

        // Iniciar proceso
        OllamaDataRepository.appendOutput("Iniciando servidor en http://127.0.0.1:11434")
        OllamaDataRepository.appendOutput("Directorio de modelos: $modelDir")

        val pb = ProcessBuilder(
            binFile.absolutePath, 
            "serve", 
            "--host", "127.0.0.1", 
            "--port", "11434", 
            "--models", modelDir
        )
        pb.redirectErrorStream(true)

        try {
            process = pb.start()
            isRunning = true

            // Iniciar hilos para leer output
            startOutputReaders()

            OllamaDataRepository.updateStatus("Servicio activo - Puerto 11434")
            OllamaDataRepository.appendOutput("Servidor Ollama iniciado correctamente")

            // Iniciar monitor de estadísticas
            startStatsMonitor()

        } catch (e: Exception) {
            OllamaDataRepository.appendOutput("ERROR al iniciar proceso: ${e.message}")
            OllamaDataRepository.updateStatus("Error: ${e.message}")
            OllamaDataRepository.setServiceRunning(false)
        }

        showNotification()
    }

    private fun startOutputReaders() {
        process?.let { proc ->
            // Hilo para output estándar
            Thread {
                try {
                    val reader = proc.inputStream.bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            OllamaDataRepository.appendOutput(it)
                            parseStatsFromOutput(it)
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        OllamaDataRepository.appendOutput("Error leyendo output: ${e.message}")
                    }
                }
            }.start()

            // Hilo para errores
            Thread {
                try {
                    val errorReader = proc.errorStream.bufferedReader()
                    var line: String?
                    while (errorReader.readLine().also { line = it } != null) {
                        line?.let {
                            OllamaDataRepository.appendOutput("[ERROR] $it")
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        OllamaDataRepository.appendOutput("Error leyendo errores: ${e.message}")
                    }
                }
            }.start()
        }
    }

    private fun startStatsMonitor() {
        Thread {
            while (isRunning) {
                try {
                    // Simular actualización de estadísticas (en una app real, leerías del proceso)
                    updateMockStats()
                    Thread.sleep(5000) // Actualizar cada 5 segundos
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    OllamaDataRepository.appendOutput("Error en monitor de stats: ${e.message}")
                }
            }
        }.start()
    }

    private fun updateMockStats() {
        // Esto es un ejemplo - en una app real obtendrías stats reales del proceso
        val stats = OllamaDataRepository.ServerStats(
            ramUsage = "${(100..500).random()} MB",
            cpuUsage = "${(5..95).random()}%",
            swapUsage = "${(0..50).random()} MB",
            tokensGenerated = "${(1000..50000).random()}",
            currentModel = "llama2",
            activeConnections = (0..5).random()
        )
        OllamaDataRepository.updateStats(stats)
    }

    private fun parseStatsFromOutput(line: String) {
        // Aquí podrías parsear estadísticas reales del output de Ollama
        when {
            line.contains("CPU", ignoreCase = true) -> {
                // Parsear uso de CPU
            }
            line.contains("memory", ignoreCase = true) -> {
                // Parsear uso de memoria
            }
            line.contains("model", ignoreCase = true) -> {
                // Parsear modelo actual
            }
        }
    }

    private fun sendCommandToProcess(command: String) {
        process?.let { proc ->
            try {
                val writer = OutputStreamWriter(proc.outputStream)
                writer.write("$command\n")
                writer.flush()
                OllamaDataRepository.appendOutput("Comando enviado: $command")
            } catch (e: Exception) {
                OllamaDataRepository.appendOutput("Error enviando comando: ${e.message}")
            }
        } ?: run {
            OllamaDataRepository.appendOutput("Error: No hay proceso activo para enviar comandos")
        }
    }

    private fun stopOllama() {
        OllamaDataRepository.updateStatus("Deteniendo servicio...")
        OllamaDataRepository.appendOutput("=== DETENIENDO SERVIDOR OLLAMA ===")
        
        isRunning = false
        
        try {
            process?.destroy()
            Runtime.getRuntime().exec("pkill -9 ollama").waitFor()
            OllamaDataRepository.appendOutput("Proceso terminado")
        } catch (e: Exception) {
            OllamaDataRepository.appendOutput("Error al detener proceso: ${e.message}")
        }
        
        process = null
        OllamaDataRepository.setServiceRunning(false)
        OllamaDataRepository.updateStatus("Servicio detenido")
        
        stopForeground(true)
        stopSelf()
    }

    private fun restartOllama() {
        OllamaDataRepository.appendOutput("=== REINICIANDO SERVIDOR OLLAMA ===")
        stopOllama()
        Thread.sleep(2000)
        startOllama()
    }

    private fun showNotification() {
        val stopIntent = Intent(this, OllamaService::class.java).setAction("STOP")
        val restartIntent = Intent(this, OllamaService::class.java).setAction("RESTART")
        
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val restartPending = PendingIntent.getService(this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE)

        // Obtener estadísticas actuales para mostrar en la notificación
        val stats = OllamaDataRepository.currentStats.value ?: OllamaDataRepository.ServerStats()
        
        // Crear la notificación con los iconos
        val notification = NotificationCompat.Builder(this, "ollama_channel")
            .setContentTitle("Servidor Ollama activo")
            .setContentText("http://localhost:11434")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Icono temporal
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            
            // Acción para detener
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, // Icono para la acción de detener
                    "Detener", 
                    stopPending
                ).build()
            )
            // Acción para reiniciar  
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_rotate, // Icono para la acción de reiniciar
                    "Reiniciar",
                    restartPending
                ).build()
            )
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Estado: activo 🟢\nRAM: ${stats.ramUsage}\nCPU: ${stats.cpuUsage}\nSwap: ${stats.swapUsage}\nTokens: ${stats.tokensGenerated}\nModelo: ${stats.currentModel}"))
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        process?.destroy()
        OllamaDataRepository.setServiceRunning(false)
        OllamaDataRepository.updateStatus("Servicio destruido")
    }
}
