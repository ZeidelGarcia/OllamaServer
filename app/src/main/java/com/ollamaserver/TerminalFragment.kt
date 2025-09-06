package com.ollamaserver

import android.graphics.Typeface
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer

class TerminalFragment : Fragment() {

    private lateinit var terminalView: TextView
    private val viewModel: OllamaViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        terminalView = view.findViewById(R.id.terminalText)
        setupTerminalView()

        // Observadores del ViewModel
        setupObservers()
    }

    private fun setupTerminalView() {
        terminalView.typeface = Typeface.MONOSPACE
        terminalView.textSize = 12f
        terminalView.setHorizontallyScrolling(true)
        terminalView.movementMethod = ScrollingMovementMethod()
        terminalView.text = "Terminal de Ollama Server\nEsperando inicio del servicio...\n"
    }

    private fun setupObservers() {
        // Observar output del terminal
        viewModel.terminalOutput.observe(viewLifecycleOwner, Observer { output ->
            terminalView.text = output
            scrollToBottom()
        })

        // Observar estado del servicio
        viewModel.serviceStatus.observe(viewLifecycleOwner, Observer { status ->
            updateTerminalStatus(status)
        })

        // Observar errores
        viewModel.errorMessage.observe(viewLifecycleOwner, Observer<String?> { error ->
            error?.let { err ->
                terminalView.append("❌ ERROR: $err\n")
                scrollToBottom()
            }
        })

        // Observar si el servicio está corriendo
        viewModel.isServiceRunning.observe(viewLifecycleOwner, Observer { isRunning ->
            if (isRunning) {
                terminalView.append("🟢 Servicio activo\n")
            } else {
                terminalView.append("❌ Servicio detenido\n")
            }
            scrollToBottom()
        })
    }

    private fun updateTerminalStatus(status: String) {
        // Actualizar UI basado en el estado
        when {
            status.contains("Error", ignoreCase = true) -> {
                terminalView.append("❌ $status\n")
            }
            status.contains("iniciando", ignoreCase = true) -> {
                terminalView.append("🟢 $status\n")
            }
            status.contains("activo", ignoreCase = true) -> {
                terminalView.append("🟢 $status\n")
            }
            else -> {
                terminalView.append("$status\n")
            }
        }
        scrollToBottom()
    }

    private fun scrollToBottom() {
        val scrollAmount = terminalView.layout?.getLineTop(terminalView.lineCount) ?: 0 - terminalView.height
        if (scrollAmount > 0) {
            terminalView.scrollTo(0, scrollAmount)
        }
    }

    fun sendCommandToService(command: String) {
        viewModel.sendTerminalCommand(command)
    }

    fun clearTerminal() {
        viewModel.clearTerminal()
    }

    fun getServiceStatus(): String {
        return viewModel.checkServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        // Restaurar scroll position
        scrollToBottom()
    }

    override fun onPause() {
        super.onPause()
        // Limpiar recursos si es necesario
    }
}
