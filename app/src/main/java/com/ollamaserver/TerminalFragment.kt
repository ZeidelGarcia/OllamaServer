package com.ollamaserver

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.io.BufferedReader
import java.io.InputStreamReader

class TerminalFragment : Fragment() {

    private lateinit var terminalView: TextView
    private val handler = Handler(Looper.getMainLooper())

    fun attachProcess(process: Process) {
        Thread {
            BufferedReader(InputStreamReader(process.inputStream)).forEachLine { line ->
                handler.post { appendLine(line) }
            }
        }.start()
        Thread {
            BufferedReader(InputStreamReader(process.errorStream)).forEachLine { line ->
                handler.post { appendLine("[ERR] $line") }
            }
        }.start()
    }

    private fun appendLine(line: String) {
        terminalView.append("$line\n")
        val scrollAmount = terminalView.layout.getLineTop(terminalView.lineCount) - terminalView.height
        if (scrollAmount > 0) terminalView.scrollTo(0, scrollAmount)
        else terminalView.scrollTo(0, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        terminalView = view.findViewById(R.id.terminalText)
        terminalView.typeface = Typeface.MONOSPACE
        terminalView.textSize = 12f
        terminalView.setHorizontallyScrolling(true)
        terminalView.movementMethod = ScrollingMovementMethod()
    }
}
