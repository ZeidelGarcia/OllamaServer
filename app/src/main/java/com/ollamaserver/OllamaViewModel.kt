package com.ollamaserver

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OllamaViewModel : ViewModel() {
    
    // Flujos de datos desde el Repository
    val terminalOutput: LiveData<String> get() = OllamaDataRepository.terminalOutput
    val serviceStatus: LiveData<String> get() = OllamaDataRepository.serviceStatus
    val isServiceRunning: LiveData<Boolean> get() = OllamaDataRepository.isServiceRunning
    val serverStats: LiveData<OllamaDataRepository.ServerStats> get() = OllamaDataRepository.serverStats
    
    // Estado interno del ViewModel
    private val _terminalInput = MutableStateFlow("")
    val terminalInput: StateFlow<String> = _terminalInput.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Comandos de control del servicio
    fun startService() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            OllamaDataRepository.updateStatus("Solicitando inicio del servicio...")
        }
    }
    
    fun stopService() {
        viewModelScope.launch {
            OllamaDataRepository.updateStatus("Solicitando detención del servicio...")
            // La detención real se manejará en el servicio a través de un Intent
        }
    }
    
    fun restartService() {
        viewModelScope.launch {
            _isLoading.value = true
            OllamaDataRepository.updateStatus("Reiniciando servicio...")
            // El reinicio real se manejará en el servicio a través de un Intent
        }
    }

    // Comandos del terminal
    fun sendTerminalCommand(command: String) {
        viewModelScope.launch {
            if (command.isNotBlank()) {
                OllamaDataRepository.appendOutput("$ $command")
                OllamaDataRepository.sendCommand(command)
                _terminalInput.value = ""
            }
        }
    }
    
    fun updateTerminalInput(input: String) {
        _terminalInput.value = input
    }
    
    fun clearTerminal() {
        viewModelScope.launch {
            OllamaDataRepository.clearTerminal()
        }
    }
    
    fun getTerminalHistory(): List<String> {
        return OllamaDataRepository.getTerminalHistory()
    }

    // Manejo de errores
    fun setError(message: String?) {
        _errorMessage.value = message
    }
    
    fun clearError() {
        _errorMessage.value = null
    }

    // Actualización de estado de carga
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    // Estadísticas del servidor
    fun updateServerStats(
        ramUsage: String? = null,
        cpuUsage: String? = null,
        swapUsage: String? = null,
        tokensGenerated: String? = null,
        currentModel: String? = null,
        activeConnections: Int? = null
    ) {
        viewModelScope.launch {
            val currentStats = OllamaDataRepository.serverStats.value ?: OllamaDataRepository.ServerStats()
            val newStats = OllamaDataRepository.ServerStats(
                ramUsage = ramUsage ?: currentStats.ramUsage,
                cpuUsage = cpuUsage ?: currentStats.cpuUsage,
                swapUsage = swapUsage ?: currentStats.swapUsage,
                tokensGenerated = tokensGenerated ?: currentStats.tokensGenerated,
                currentModel = currentModel ?: currentStats.currentModel,
                activeConnections = activeConnections ?: currentStats.activeConnections
            )
            OllamaDataRepository.updateStats(newStats)
        }
    }

    // Verificación del estado del servicio
    fun checkServiceStatus(): String {
        return OllamaDataRepository.serviceStatus.value ?: "Estado desconocido"
    }

    // Limpieza de recursos
    override fun onCleared() {
        super.onCleared()
        // Limpiar recursos si es necesario
        _errorMessage.value = null
        _isLoading.value = false
    }
}
