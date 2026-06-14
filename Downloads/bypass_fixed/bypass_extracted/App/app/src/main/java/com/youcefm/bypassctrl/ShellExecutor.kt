package com.youcefm.bypassctrl

import java.io.BufferedReader
import java.io.InputStreamReader

object ShellExecutor {

    fun exec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = StringBuilder()
            val error = StringBuilder()

            val outputThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.forEachLine { output.append(it).append("\n") }
                }
            }
            val errorThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.forEachLine { error.append(it).append("\n") }
                }
            }

            outputThread.start()
            errorThread.start()
            process.waitFor()
            outputThread.join(1000)
            errorThread.join(1000)

            output.toString().trim().ifEmpty { error.toString().trim() }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
