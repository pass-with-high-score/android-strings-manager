package app.pwhs.androidstringsmanager.actions

import app.pwhs.androidstringsmanager.services.StringsResService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

class CleanAction : BaseResAction() {

    override fun perform(project: Project, res: VirtualFile) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Cleaning non-translatable strings", true) {
            override fun run(indicator: ProgressIndicator) {
                val report = project.service<StringsResService>().cleanLocales(res)
                ApplicationManager.getApplication().invokeLater {
                    if (report.missingDefault) {
                        Messages.showErrorDialog(project, "values/strings.xml not found under ${res.path}", "Android Strings Manager")
                        return@invokeLater
                    }
                    val lines = buildString {
                        append("translatable=\"false\" entries in default: ${report.translatableFalseCount}\n")
                        append("total removed across locales: ${report.total}\n\n")
                        for ((f, n) in report.perFile) append("${f.parent.name}: $n\n")
                    }
                    Messages.showInfoMessage(project, lines, "Clean complete")
                }
            }
        })
    }
}
