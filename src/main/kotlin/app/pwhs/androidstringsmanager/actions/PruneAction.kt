package app.pwhs.androidstringsmanager.actions

import app.pwhs.androidstringsmanager.services.PruneResult
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
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PruneAction : BaseResAction() {

    override fun perform(project: Project, res: VirtualFile) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Scanning sources for string references", true) {
            override fun run(indicator: ProgressIndicator) {
                val result = project.service<StringsResService>().findUnused(res, keep = emptySet())
                ApplicationManager.getApplication().invokeLater {
                    if (result.unused.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            "No unused strings found.\nDefault: ${result.totalDefault}, referenced: ${result.referencedCount}",
                            "Prune"
                        )
                        return@invokeLater
                    }
                    val dialog = UnusedDialog(project, result)
                    if (dialog.showAndGet()) {
                        val selected = dialog.selectedNames()
                        if (selected.isEmpty()) return@invokeLater
                        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Removing unused strings", true) {
                            override fun run(indicator: ProgressIndicator) {
                                val removed = project.service<StringsResService>().removeNames(res, selected)
                                ApplicationManager.getApplication().invokeLater {
                                    Messages.showInfoMessage(project, "Removed $removed entries across strings.xml files.", "Prune complete")
                                }
                            }
                        })
                    }
                }
            }
        })
    }
}

private class UnusedDialog(project: Project, private val result: PruneResult) : DialogWrapper(project) {

    private val list = CheckBoxList<String>()

    init {
        title = "Unused strings"
        setOKButtonText("Delete selected")
        setCancelButtonText("Close")
        val names = result.unused.map { it.first }
        list.setItems(names) { it }
        names.forEach { list.setItemSelected(it, true) }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val header = JBLabel(
            "Default: ${result.totalDefault}  ·  referenced: ${result.referencedCount}  ·  unused: ${result.unused.size}"
        ).apply { border = JBUI.Borders.emptyBottom(8) }
        val scroll = JBScrollPane(list).apply { preferredSize = Dimension(520, 420) }
        val wrapper = JPanel(BorderLayout())
        val top = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(header)
            add(JBLabel("Uncheck any key you want to keep. Deletion removes from values/ and every values-*/strings.xml."))
        }
        wrapper.add(top, BorderLayout.NORTH)
        wrapper.add(scroll, BorderLayout.CENTER)
        wrapper.border = JBUI.Borders.empty(8)
        return wrapper
    }

    fun selectedNames(): Set<String> {
        val out = mutableSetOf<String>()
        for (i in 0 until list.itemsCount) {
            val item = list.getItemAt(i) ?: continue
            if (list.isItemSelected(i)) out += item
        }
        return out
    }
}
