package app.pwhs.androidstringsmanager.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class BaseResAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val res = resolveRes(e) ?: pickResDir(project) ?: return
        perform(project, res)
    }

    protected abstract fun perform(project: Project, res: VirtualFile)

    private fun resolveRes(e: AnActionEvent): VirtualFile? {
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        var cur: VirtualFile? = vf
        while (cur != null) {
            if (cur.isDirectory && cur.name == "res" && cur.findChild("values")?.findChild("strings.xml") != null) {
                return cur
            }
            cur = cur.parent
        }
        return null
    }

    private fun pickResDir(project: Project): VirtualFile? {
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle("Select res/ Directory")
            .withDescription("Choose the Android res/ folder (must contain values/strings.xml)")
        val chosen = FileChooser.chooseFile(descriptor, project, project.guessBaseDir()) ?: return null
        if (chosen.findChild("values")?.findChild("strings.xml") == null) return null
        return chosen
    }

    private fun Project.guessBaseDir(): VirtualFile? =
        com.intellij.openapi.roots.ProjectRootManager.getInstance(this).contentRoots.firstOrNull()
}
