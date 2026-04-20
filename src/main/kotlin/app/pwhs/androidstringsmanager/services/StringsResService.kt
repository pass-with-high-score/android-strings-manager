package app.pwhs.androidstringsmanager.services

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.StringWriter

data class LocaleMissing(val locale: String, val name: String, val defaultValue: String)

data class PruneResult(
    val totalDefault: Int,
    val referencedCount: Int,
    val unused: List<Pair<String, String>>,
)

@Service(Service.Level.PROJECT)
class StringsResService(private val project: Project) {

    companion object {
        private val TAGS = setOf("string", "string-array", "plurals")
        private val R_STRING = Regex("""\bR\.string\.([A-Za-z0-9_]+)""")
        private val AT_STRING = Regex("""@string/([A-Za-z0-9_]+)""")
        private val TOOLS_KEEP = Regex("""tools:keep\s*=\s*"([^"]*)"""")
        private val SKIP_DIRS = setOf("build", ".gradle", ".idea", "generated", ".git", "node_modules")
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
        val indent = ReadAction.compute<Int, RuntimeException> {
            runCatching { CodeStyle.getIndentOptions(project, vf).INDENT_SIZE }
                .getOrDefault(4)
                .takeIf { it > 0 } ?: 4
        }
        val format = Format.getPrettyFormat()
            .setIndent(" ".repeat(indent))
            .setLineSeparator("\n")
        val body = StringWriter().also { XMLOutputter(format).output(root, it) }.toString()
        val text = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n$body\n"
        WriteAction.runAndWait<Throwable> {
            VfsUtil.saveText(vf, text)
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

    private fun collectUsedNames(defaultXml: VirtualFile): Set<String> = ReadAction.compute<Set<String>, RuntimeException> {
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
}

data class CleanReport(
    val translatableFalseCount: Int,
    val perFile: List<Pair<VirtualFile, Int>>,
    val missingDefault: Boolean = false,
) {
    val total: Int get() = perFile.sumOf { it.second }
}
