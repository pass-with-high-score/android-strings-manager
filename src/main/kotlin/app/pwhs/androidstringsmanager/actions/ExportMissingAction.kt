package app.pwhs.androidstringsmanager.actions

import app.pwhs.androidstringsmanager.services.StringsResService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class ExportMissingAction : BaseResAction() {

    override fun perform(project: Project, res: VirtualFile) {
        val saver = FileChooserFactory.getInstance().createSaveFileDialog(
            FileSaverDescriptor("Export missing translations", "Choose a CSV file", "csv"),
            project
        )
        val baseDir: Path? = project.basePath?.let { Path.of(it) }
        val wrapper = saver.save(baseDir, "missing_translations.csv") ?: return
        val target = wrapper.file

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Exporting missing translations", true) {
            override fun run(indicator: ProgressIndicator) {
                val rows = project.service<StringsResService>().exportMissing(res)
                target.parentFile?.mkdirs()
                OutputStreamWriter(target.outputStream(), StandardCharsets.UTF_8).use { w ->
                    w.write("locale,name,default_value\n")
                    for (r in rows) w.write("${csv(r.locale)},${csv(r.name)},${csv(r.defaultValue)}\n")
                }
                ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage(
                        project,
                        "Missing entries: ${rows.size}\nWritten to: ${target.absolutePath}",
                        "Export complete"
                    )
                }
            }
        })
    }

    private fun csv(s: String): String {
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
    }
}
