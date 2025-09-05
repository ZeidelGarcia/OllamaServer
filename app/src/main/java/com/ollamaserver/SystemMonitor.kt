package com.ollamaserver

// Aquí se puede implementar la recolección de RAM, CPU, Swap y tokens si se desea
class SystemMonitor {
    companion object {
        fun getRAMUsage(): String = "--"
        fun getCPUUsage(): String = "--"
        fun getSwapUsage(): String = "--"
        fun getTokens(): String = "--"
    }
}
