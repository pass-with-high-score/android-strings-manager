package app.pwhs.androidstringsmanager.actions

import app.pwhs.androidstringsmanager.services.CsvRow
import app.pwhs.androidstringsmanager.services.StringsResService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

class ImportCsvAction : BaseResAction() {

    override fun perform(project: Project, res: VirtualFile) {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("csv")
            .withTitle("Select CSV to import")
            .withDescription("CSV with columns: locale,name,value (or locale,name,default_value)")
        val file = FileChooser.chooseFile(descriptor, project, null) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Importing translations", true) {
            override fun run(indicator: ProgressIndicator) {
                val text = String(file.contentsToByteArray(), file.charset)
                val rows = parseCsvRows(text)
                val stats = project.service<StringsResService>().importCsv(res, rows)
                ApplicationManager.getApplication().invokeLater {
                    val msg = buildString {
                        append("Added: ${stats.added}\n")
                        append("Updated: ${stats.updated}\n")
                        append("Files touched: ${stats.filesTouched}\n")
                        append("Skipped rows: ${stats.skippedRows}\n")
                        if (stats.createdLocales.isNotEmpty()) {
                            append("Created locales: ${stats.createdLocales.joinToString()}\n")
                        }
                    }
                    Messages.showInfoMessage(project, msg, "Import complete")
                }
            }
        })
    }

    private fun parseCsvRows(text: String): List<CsvRow> {
        val raw = parseCsv(text)
        if (raw.isEmpty()) return emptyList()
        val header = raw.first().map { it.trim().lowercase() }
        val hasHeader = header.size >= 3 && header[0] == "locale" && header[1] == "name"
        val body = if (hasHeader) raw.drop(1) else raw
        return body.mapNotNull { cols ->
            if (cols.size < 3) return@mapNotNull null
            CsvRow(cols[0], cols[1], cols[2])
        }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuote = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (inQuote) {
                when {
                    c == '"' && i + 1 < n && text[i + 1] == '"' -> { cur.append('"'); i += 2 }
                    c == '"' -> { inQuote = false; i++ }
                    else -> { cur.append(c); i++ }
                }
            } else {
                when (c) {
                    '"' -> { inQuote = true; i++ }
                    ',' -> { row.add(cur.toString()); cur.setLength(0); i++ }
                    '\r' -> { i++ }
                    '\n' -> {
                        row.add(cur.toString()); cur.setLength(0)
                        rows.add(row.toList()); row.clear(); i++
                    }
                    else -> { cur.append(c); i++ }
                }
            }
        }
        if (cur.isNotEmpty() || row.isNotEmpty()) {
            row.add(cur.toString())
            rows.add(row.toList())
        }
        return rows
    }
}
