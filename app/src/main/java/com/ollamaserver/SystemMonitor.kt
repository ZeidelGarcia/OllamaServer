package com.ollamaserver

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.StatFs
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

class SystemMonitor(private val context: Context) {
    
    private val prevCpuTime = AtomicLong(0)
    private val prevAppCpuTime = AtomicLong(0)
    private val tokensGenerated = AtomicLong(0)
    
    companion object {
        private const val CPU_UPDATE_INTERVAL = 1000L // 1 segundo
        private var lastCpuUpdateTime = 0L
        private var lastCpuUsage = 0.0
        
        fun getRAMUsage(context: Context): String {
            return try {
                val memoryInfo = ActivityManager.MemoryInfo()
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.getMemoryInfo(memoryInfo)
                
                val totalMemory = memoryInfo.totalMem
                val availableMemory = memoryInfo.availMem
                val usedMemory = totalMemory - availableMemory
                
                formatBytes(usedMemory)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
        
        fun getCPUUsage(): String {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCpuUpdateTime < CPU_UPDATE_INTERVAL) {
                return "${lastCpuUsage.toInt()}%"
            }
            
            return try {
                val cpuStatFile = RandomAccessFile("/proc/stat", "r")
                val firstLine = cpuStatFile.readLine()
                cpuStatFile.close()
                
                val parts = firstLine.split("\\s+".toRegex())
                if (parts.size >= 5) {
                    val user = parts[1].toLong()
                    val nice = parts[2].toLong()
                    val system = parts[3].toLong()
                    val idle = parts[4].toLong()
                    
                    val totalCpuTime = user + nice + system + idle
                    val cpuUsage = calculateCpuUsage(totalCpuTime, idle)
                    
                    lastCpuUpdateTime = currentTime
                    lastCpuUsage = cpuUsage
                    
                    "${cpuUsage.toInt()}%"
                } else {
                    "--"
                }
            } catch (e: Exception) {
                "--"
            }
        }
        
        private fun calculateCpuUsage(totalCpuTime: Long, idleTime: Long): Double {
            // Implementación simplificada - en una app real necesitarías muestreo temporal
            return (totalCpuTime - idleTime).toDouble() / totalCpuTime * 100
        }
        
        fun getSwapUsage(context: Context): String {
            return try {
                val memoryInfo = ActivityManager.MemoryInfo()
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.getMemoryInfo(memoryInfo)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    getSwapUsageLegacy()
                } else {
                    // Método alternativo para versiones anteriores
                    getSwapUsageLegacy()
                }
            } catch (e: Exception) {
                "--"
            }
        }
        
        private fun getSwapUsageLegacy(): String {
            return try {
                val reader = BufferedReader(FileReader("/proc/meminfo"))
                var line: String?
                var totalSwap = 0L
                var freeSwap = 0L
                
                while (reader.readLine().also { line = it } != null) {
                    when {
                        line?.startsWith("SwapTotal:") == true -> {
                            totalSwap = extractMemoryValue(line)
                        }
                        line?.startsWith("SwapFree:") == true -> {
                            freeSwap = extractMemoryValue(line)
                        }
                    }
                }
                reader.close()
                
                val usedSwap = totalSwap - freeSwap
                formatBytes(usedSwap * 1024) // Convertir de KB a bytes
            } catch (e: Exception) {
                "--"
            }
        }
        
        private fun extractMemoryValue(line: String?): Long {
            return line?.replace("\\s+".toRegex(), " ")?.split(" ")?.get(1)?.toLongOrNull() ?: 0L
        }
        
        fun getAppRAMUsage(context: Context): String {
            return try {
                val memoryInfo = Debug.MemoryInfo()
                Debug.getMemoryInfo(memoryInfo)
                
                val totalPss = memoryInfo.totalPss.toLong() * 1024 // Convertir de KB a bytes
                formatBytes(totalPss)
            } catch (e: Exception) {
                "--"
            }
        }
        
        fun getStorageUsage(context: Context): String {
            return try {
                val path = context.filesDir.absolutePath
                val stat = StatFs(path)
                val blockSize = stat.blockSizeLong
                val totalBlocks = stat.blockCountLong
                val availableBlocks = stat.availableBlocksLong
                
                val usedSpace = (totalBlocks - availableBlocks) * blockSize
                formatBytes(usedSpace)
            } catch (e: Exception) {
                "--"
            }
        }
        
        private fun formatBytes(bytes: Long): String {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var size = bytes.toDouble()
            var unitIndex = 0
            
            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }
            
            return "%.1f %s".format(size, units[unitIndex])
        }
    }
    
    fun incrementTokens(count: Long = 1) {
        tokensGenerated.addAndGet(count)
    }
    
    fun getTokensGenerated(): String {
        return tokensGenerated.toString()
    }
    
    fun getAppCPUUsage(): String {
        return try {
            val pid = Process.myPid()
            val statFile = RandomAccessFile("/proc/$pid/stat", "r")
            val stats = statFile.readLine().split("\\s+".toRegex())
            statFile.close()
            
            if (stats.size > 15) {
                val utime = stats[13].toLong()
                val stime = stats[14].toLong()
                val totalAppCpuTime = utime + stime
                
                val cpuUsage = calculateAppCpuUsage(totalAppCpuTime)
                "${cpuUsage.toInt()}%"
            } else {
                "--"
            }
        } catch (e: Exception) {
            "--"
        }
    }
    
    private fun calculateAppCpuUsage(currentAppCpuTime: Long): Double {
        val currentTotalCpuTime = getTotalCpuTime()
        val appCpuDiff = currentAppCpuTime - prevAppCpuTime.get()
        val totalCpuDiff = currentTotalCpuTime - prevCpuTime.get()
        
        prevAppCpuTime.set(currentAppCpuTime)
        prevCpuTime.set(currentTotalCpuTime)
        
        return if (totalCpuDiff > 0) {
            (appCpuDiff.toDouble() / totalCpuDiff) * 100
        } else {
            0.0
        }
    }
    
    private fun getTotalCpuTime(): Long {
        return try {
            val cpuStatFile = RandomAccessFile("/proc/stat", "r")
            val firstLine = cpuStatFile.readLine()
            cpuStatFile.close()
            
            val parts = firstLine.split("\\s+".toRegex())
            if (parts.size >= 5) {
                parts[1].toLong() + parts[2].toLong() + parts[3].toLong() + parts[4].toLong()
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    fun resetTokens() {
        tokensGenerated.set(0)
    }
}
