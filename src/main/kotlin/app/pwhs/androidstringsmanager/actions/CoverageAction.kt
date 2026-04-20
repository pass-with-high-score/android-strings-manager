package app.pwhs.androidstringsmanager.actions

import app.pwhs.androidstringsmanager.services.LocaleCoverage
import app.pwhs.androidstringsmanager.services.StringsResService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

class CoverageAction : BaseResAction() {

    override fun perform(project: Project, res: VirtualFile) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Computing translation coverage", true) {
            override fun run(indicator: ProgressIndicator) {
                val rows = project.service<StringsResService>().coverage(res)
                ApplicationManager.getApplication().invokeLater {
                    if (rows.isEmpty()) {
                        Messages.showInfoMessage(project, "No values-* locales found under ${res.path}.", "Translation coverage")
                        return@invokeLater
                    }
                    CoverageDialog(project, rows).show()
                }
            }
        })
    }
}

private class CoverageDialog(project: Project, private val rows: List<LocaleCoverage>) : DialogWrapper(project) {
    init {
        title = "Translation coverage"
        setOKButtonText("Close")
        init()
    }

    override fun createActions() = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val total = rows.firstOrNull()?.total ?: 0
        val columns = arrayOf("Locale", "Translated", "Missing", "Total", "%")
        val data = rows
            .sortedByDescending { it.percent }
            .map { arrayOf<Any>(it.locale, it.translated, it.missing, it.total, "${it.percent}%") }
            .toTypedArray()
        val model = object : DefaultTableModel(data, columns) {
            override fun isCellEditable(row: Int, col: Int) = false
        }
        val table = JTable(model).apply {
            autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        }
        val scroll = JBScrollPane(table).apply { preferredSize = Dimension(480, 360) }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(javax.swing.JLabel("Default locale: $total keys").apply { border = JBUI.Borders.emptyBottom(6) }, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }
    }
}
