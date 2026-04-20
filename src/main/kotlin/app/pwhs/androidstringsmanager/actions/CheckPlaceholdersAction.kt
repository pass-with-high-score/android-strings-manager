package app.pwhs.androidstringsmanager.actions

import app.pwhs.androidstringsmanager.services.PlaceholderIssue
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

class CheckPlaceholdersAction : BaseResAction() {

    override fun perform(project: Project, res: VirtualFile) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Checking placeholder consistency", true) {
            override fun run(indicator: ProgressIndicator) {
                val issues = project.service<StringsResService>().checkPlaceholders(res)
                ApplicationManager.getApplication().invokeLater {
                    if (issues.isEmpty()) {
                        Messages.showInfoMessage(project, "No placeholder mismatches found.", "Placeholder check")
                        return@invokeLater
                    }
                    PlaceholderDialog(project, issues).show()
                }
            }
        })
    }
}

private class PlaceholderDialog(project: Project, private val issues: List<PlaceholderIssue>) : DialogWrapper(project) {
    init {
        title = "Placeholder mismatches: ${issues.size}"
        setOKButtonText("Close")
        setCancelButtonText("Close")
        init()
    }

    override fun createActions() = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val columns = arrayOf("Locale", "Name", "Default", "Locale value", "Expected", "Found")
        val data = issues.map {
            arrayOf(
                it.locale,
                it.name,
                it.defaultText,
                it.localeText,
                it.defaultPlaceholders.joinToString(" "),
                it.localePlaceholders.joinToString(" "),
            )
        }.toTypedArray()
        val model = object : DefaultTableModel(data, columns) {
            override fun isCellEditable(row: Int, col: Int) = false
        }
        val table = JTable(model).apply {
            autoResizeMode = JTable.AUTO_RESIZE_OFF
            columnModel.getColumn(0).preferredWidth = 60
            columnModel.getColumn(1).preferredWidth = 180
            columnModel.getColumn(2).preferredWidth = 240
            columnModel.getColumn(3).preferredWidth = 240
            columnModel.getColumn(4).preferredWidth = 100
            columnModel.getColumn(5).preferredWidth = 100
        }
        val scroll = JBScrollPane(table).apply { preferredSize = Dimension(860, 480) }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
        }
    }
}
