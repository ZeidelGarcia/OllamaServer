package com.ollamaserver

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class OllamaService : Service() {

    private var process: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startOllama()
            "STOP" -> stopOllama()
            "RESTART" -> restartOllama()
        }
        return START_STICKY
    }

    private fun startOllama() {
        if (process != null) return
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val modelDir = prefs.getString("ollama_dir", filesDir.absolutePath + "/.ollama") ?: return

        val binFile = File(filesDir, "bin/ollama")
        if (!binFile.exists()) {
            assets.open("bin/ollama_aarch64").use { input -> binFile.outputStream().use { output -> input.copyTo(output) } }
            binFile.setExecutable(true)
        }

        val pb = ProcessBuilder(binFile.absolutePath, "serve", "--host", "127.0.0.1", "--port", "11434", "--models", modelDir)
        pb.redirectErrorStream(true)
        process = pb.start()

        // TerminalFragment debe adjuntar attachProcess(process) desde MainActivity

        showNotification()
    }

    private fun stopOllama() {
        process?.destroy()
        Runtime.getRuntime().exec("pkill -9 ollama")
        process = null
        stopForeground(true)
        stopSelf()
    }

    private fun restartOllama() {
        stopOllama()
        Thread.sleep(1000)
        startOllama()
    }

    private fun showNotification() {
        val stopIntent = PendingIntent.getService(this, 0, Intent(this, OllamaService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val restartIntent = PendingIntent.getService(this, 0, Intent(this, OllamaService::class.java).setAction("RESTART"), PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "ollama_channel")
            .setContentTitle("Servidor Ollama activo")
            .setContentText("http://localhost:11434")
            .setSmallIcon(R.drawable.icon_server)
            .setOngoing(true)
            .addAction(R.drawable.icon_server, "Detener", stopIntent)
            .addAction(R.drawable.icon_server, "Reiniciar", restartIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Estado: activo\nRAM: --\nCPU: --\nSwap: --\nTokens: --\nModelo: --"))
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
