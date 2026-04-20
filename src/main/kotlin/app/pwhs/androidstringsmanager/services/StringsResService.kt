package app.pwhs.androidstringsmanager.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jdom.Element

data class LocaleMissing(val locale: String, val name: String, val defaultValue: String)

data class PruneResult(
    val totalDefault: Int,
    val referencedCount: Int,
    val unused: List<Pair<String, String>>,
)

data class CsvRow(val locale: String, val name: String, val value: String)

data class ImportStats(
    val added: Int,
    val updated: Int,
    val filesTouched: Int,
    val createdLocales: List<String>,
    val skippedRows: Int,
)

data class PlaceholderIssue(
    val name: String,
    val locale: String,
    val defaultPlaceholders: List<String>,
    val localePlaceholders: List<String>,
    val defaultText: String,
    val localeText: String,
)

data class LocaleCoverage(val locale: String, val translated: Int, val total: Int) {
    val missing: Int get() = total - translated
    val percent: Int get() = if (total == 0) 100 else (translated * 100 / total)
}

@Service(Service.Level.PROJECT)
class StringsResService(private val project: Project) {

    companion object {
        private val TAGS = setOf("string", "string-array", "plurals")
        private val R_STRING = Regex("""\bR\.string\.([A-Za-z0-9_]+)""")
        private val AT_STRING = Regex("""@string/([A-Za-z0-9_]+)""")
        private val TOOLS_KEEP = Regex("""tools:keep\s*=\s*"([^"]*)"""")
        private val SKIP_DIRS = setOf("build", ".gradle", ".idea", "generated", ".git", "node_modules")
        private val PLACEHOLDER_RE = Regex("""%(?:\d+\$)?[a-zA-Z]|%%""")
    }

    fun defaultStringsFile(res: VirtualFile): VirtualFile? =
        res.findChild("values")?.findChild("strings.xml")

    fun localeStringsFiles(res: VirtualFile): List<VirtualFile> =
        res.children
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .mapNotNull { it.findChild("strings.xml") }
            .sortedBy { it.parent.name }

    private fun loadRoot(vf: VirtualFile): Element =
        vf.inputStream.use { JDOMUtil.load(it) }

    private fun saveRoot(vf: VirtualFile, root: Element) {
        val body = JDOMUtil.writeElement(root, "\n")
        val text = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n$body\n"
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project, "Update strings.xml", null, Runnable {
                VfsUtil.saveText(vf, text)
                val docManager = PsiDocumentManager.getInstance(project)
                docManager.commitAllDocuments()
                val psi = PsiManager.getInstance(project).findFile(vf)
                if (psi != null) CodeStyleManager.getInstance(project).reformat(psi)
            })
        }
    }

    private fun isTag(el: Element) = el.name in TAGS

    fun cleanLocales(res: VirtualFile): CleanReport {
        val default = defaultStringsFile(res)
            ?: return CleanReport(0, emptyList(), missingDefault = true)
        val root = loadRoot(default)
        val remove = root.children
            .filter { isTag(it) && it.getAttributeValue("translatable")?.equals("false", true) == true }
            .mapNotNull { it.getAttributeValue("name") }
            .toSet()
        if (remove.isEmpty()) return CleanReport(0, emptyList())

        val perFile = mutableListOf<Pair<VirtualFile, Int>>()
        for (f in localeStringsFiles(res)) {
            val r = loadRoot(f)
            val toRemove = r.children.filter { isTag(it) && it.getAttributeValue("name") in remove }
            if (toRemove.isNotEmpty()) {
                toRemove.forEach { r.removeContent(it) }
                saveRoot(f, r)
                perFile.add(f to toRemove.size)
            }
        }
        return CleanReport(remove.size, perFile)
    }

    fun exportMissing(res: VirtualFile): List<LocaleMissing> {
        val default = defaultStringsFile(res) ?: return emptyList()
        val root = loadRoot(default)
        val entries = linkedMapOf<String, String>()
        for (el in root.children) {
            if (el.name != "string") continue
            if (el.getAttributeValue("translatable")?.equals("false", true) == true) continue
            val name = el.getAttributeValue("name") ?: continue
            entries[name] = (el.text ?: "").trim()
        }
        val out = mutableListOf<LocaleMissing>()
        for (f in localeStringsFiles(res)) {
            val locale = f.parent.name.removePrefix("values-")
            val have = loadRoot(f).children
                .filter { isTag(it) }
                .mapNotNull { it.getAttributeValue("name") }
                .toSet()
            for ((name, value) in entries) {
                if (name !in have) out.add(LocaleMissing(locale, name, value))
            }
        }
        return out
    }

    fun findUnused(res: VirtualFile, keep: Set<String>): PruneResult {
        val default = defaultStringsFile(res) ?: return PruneResult(0, 0, emptyList())
        val root = loadRoot(default)
        val entries = linkedMapOf<String, String>()
        for (el in root.children) {
            if (el.name != "string") continue
            val name = el.getAttributeValue("name") ?: continue
            entries[name] = (el.text ?: "").trim()
        }
        val used = collectUsedNames(default)
        val unused = entries.filter { (n, _) -> n !in used && n !in keep }.toList()
        return PruneResult(entries.size, used.size, unused)
    }

    private fun collectUsedNames(defaultXml: VirtualFile): Set<String> = runReadAction {
        val used = mutableSetOf<String>()
        val index = ProjectFileIndex.getInstance(project)
        index.iterateContent { vf ->
            if (vf.isDirectory) return@iterateContent true
            if (vf.path.split('/').any { it in SKIP_DIRS }) return@iterateContent true
            val ext = vf.extension?.lowercase() ?: return@iterateContent true
            when (ext) {
                "kt", "java" -> {
                    val text = vf.readTextSafe() ?: return@iterateContent true
                    R_STRING.findAll(text).forEach { used += it.groupValues[1] }
                }
                "xml" -> {
                    if (vf == defaultXml) return@iterateContent true
                    val text = vf.readTextSafe() ?: return@iterateContent true
                    AT_STRING.findAll(text).forEach { used += it.groupValues[1] }
                    TOOLS_KEEP.findAll(text).forEach { m ->
                        AT_STRING.findAll(m.groupValues[1]).forEach { used += it.groupValues[1] }
                    }
                }
            }
            true
        }
        used
    }

    fun removeNames(res: VirtualFile, names: Set<String>): Int {
        if (names.isEmpty()) return 0
        var total = 0
        val files = buildList {
            defaultStringsFile(res)?.let { add(it) }
            addAll(localeStringsFiles(res))
        }
        for (f in files) {
            val root = loadRoot(f)
            val toRemove = root.children.filter { isTag(it) && it.getAttributeValue("name") in names }
            if (toRemove.isNotEmpty()) {
                toRemove.forEach { root.removeContent(it) }
                saveRoot(f, root)
                total += toRemove.size
            }
        }
        return total
    }

    private fun VirtualFile.readTextSafe(): String? = try {
        String(contentsToByteArray(), charset)
    } catch (_: Throwable) {
        null
    }

    fun importCsv(res: VirtualFile, rows: List<CsvRow>): ImportStats {
        val byLocale = rows.groupBy { it.locale.trim() }.filterKeys { it.isNotEmpty() }
        var added = 0
        var updated = 0
        var skipped = 0
        var filesTouched = 0
        val createdLocales = mutableListOf<String>()
        for ((locale, entries) in byLocale) {
            val file = ensureLocaleFile(res, locale) { createdLocales += locale }
            if (file == null) {
                skipped += entries.size
                continue
            }
            val root = loadRoot(file)
            var changed = false
            for (row in entries) {
                val name = row.name.trim()
                if (name.isEmpty()) { skipped++; continue }
                val existing = root.children.firstOrNull { it.name == "string" && it.getAttributeValue("name") == name }
                if (existing == null) {
                    val el = Element("string").apply {
                        setAttribute("name", name)
                        text = row.value
                    }
                    root.addContent(el)
                    added++
                    changed = true
                } else {
                    if ((existing.text ?: "") != row.value) {
                        existing.text = row.value
                        updated++
                        changed = true
                    } else {
                        skipped++
                    }
                }
            }
            if (changed) {
                saveRoot(file, root)
                filesTouched++
            }
        }
        return ImportStats(added, updated, filesTouched, createdLocales, skipped)
    }

    private fun ensureLocaleFile(res: VirtualFile, locale: String, onCreated: () -> Unit): VirtualFile? {
        val dirName = "values-$locale"
        val existingDir = res.findChild(dirName)
        if (existingDir != null) return existingDir.findChild("strings.xml") ?: createStringsXml(existingDir, onCreated = {})
        return createStringsXml(res, parentName = dirName, onCreated = onCreated)
    }

    private fun createStringsXml(parentOrRes: VirtualFile, parentName: String? = null, onCreated: () -> Unit): VirtualFile? {
        var created: VirtualFile? = null
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project, "Create strings.xml", null, Runnable {
                val parent = if (parentName != null) parentOrRes.createChildDirectory(this, parentName) else parentOrRes
                val file = parent.createChildData(this, "strings.xml")
                VfsUtil.saveText(file, "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n")
                created = file
                if (parentName != null) onCreated()
            })
        }
        return created
    }

    fun checkPlaceholders(res: VirtualFile): List<PlaceholderIssue> {
        val default = defaultStringsFile(res) ?: return emptyList()
        val defaults = loadRoot(default).children
            .filter { it.name == "string" }
            .mapNotNull { el ->
                val name = el.getAttributeValue("name") ?: return@mapNotNull null
                if (el.getAttributeValue("translatable")?.equals("false", true) == true) return@mapNotNull null
                name to (el.text ?: "")
            }
            .toMap()
        val issues = mutableListOf<PlaceholderIssue>()
        for (f in localeStringsFiles(res)) {
            val locale = f.parent.name.removePrefix("values-")
            for (el in loadRoot(f).children) {
                if (el.name != "string") continue
                val name = el.getAttributeValue("name") ?: continue
                val defText = defaults[name] ?: continue
                val locText = el.text ?: ""
                val dp = extractPlaceholders(defText)
                val lp = extractPlaceholders(locText)
                if (dp.sorted() != lp.sorted()) {
                    issues += PlaceholderIssue(name, locale, dp, lp, defText, locText)
                }
            }
        }
        return issues
    }

    private fun extractPlaceholders(s: String): List<String> =
        PLACEHOLDER_RE.findAll(s).map { it.value }.filter { it != "%%" }.toList()

    fun coverage(res: VirtualFile): List<LocaleCoverage> {
        val default = defaultStringsFile(res) ?: return emptyList()
        val names = loadRoot(default).children
            .filter { it.name == "string" && it.getAttributeValue("translatable")?.equals("false", true) != true }
            .mapNotNull { it.getAttributeValue("name") }
            .toSet()
        val total = names.size
        val out = mutableListOf<LocaleCoverage>()
        for (f in localeStringsFiles(res)) {
            val locale = f.parent.name.removePrefix("values-")
            val have = loadRoot(f).children
                .filter { it.name == "string" }
                .mapNotNull { it.getAttributeValue("name") }
                .filter { it in names }
                .toSet()
            out += LocaleCoverage(locale, have.size, total)
        }
        return out
    }
}

data class CleanReport(
    val translatableFalseCount: Int,
    val perFile: List<Pair<VirtualFile, Int>>,
    val missingDefault: Boolean = false,
) {
    val total: Int get() = perFile.sumOf { it.second }
}
