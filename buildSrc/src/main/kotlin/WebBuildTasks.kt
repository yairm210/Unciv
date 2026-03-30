@file:Suppress("InvalidPackageDeclaration")
package com.unciv.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val importRegex = Regex("^\\s*import\\s+([A-Za-z0-9_.]+)", setOf(RegexOption.MULTILINE))
private val blockCommentRegex = Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL))
private val lineCommentRegex = Regex("//.*$")
private val builtinTypeNames = setOf(
    "Any", "Array", "Boolean", "Byte", "Char", "Collection", "Double", "Float", "Int", "Iterable",
    "List", "Long", "Map", "MutableCollection", "MutableIterable", "MutableList", "MutableMap",
    "MutableSet", "Nothing", "Pair", "Sequence", "Set", "Short", "String", "Throwable", "Triple", "Unit"
)

@CacheableTask
abstract class GenerateWebJsTestSuiteTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testsSourceRoot: DirectoryProperty

    @get:OutputDirectory
    abstract val generatedTestsDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputRoot = generatedTestsDir.get().asFile
        val outputFile = outputRoot.resolve("com/unciv/app/web/WebJsTestSuite.kt")
        outputFile.parentFile.mkdirs()

        val packageRegex = Regex("^\\s*package\\s+([A-Za-z0-9_.]+)", setOf(RegexOption.MULTILINE))
        val classRegex = Regex("^\\s*(?:class|object)\\s+([A-Za-z0-9_]+)", setOf(RegexOption.MULTILINE))
        val methodRegex = Regex("^\\s*(?:public\\s+|private\\s+|internal\\s+|protected\\s+)?(?:suspend\\s+)?fun\\s+([A-Za-z0-9_]+)\\s*\\(")

        val candidateFiles = testsSourceRoot.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { file ->
                val normalized = file.invariantSeparatorsPath
                normalized.contains("/com/unciv/dev/")
                    || normalized.endsWith("/com/unciv/testing/GdxTestRunner.kt")
            }
            .sortedBy { it.invariantSeparatorsPath }
            .toList()

        val body = buildString {
            appendLine("package com.unciv.app.web")
            appendLine()
            appendLine("data class WebJsGeneratedTestMethod(")
            appendLine("    val name: String,")
            appendLine("    val ignoredReason: String?,")
            appendLine("    val execute: (Any) -> Unit,")
            appendLine(")")
            appendLine()
            appendLine("data class WebJsGeneratedTestClass(")
            appendLine("    val className: String,")
            appendLine("    val createInstance: () -> Any,")
            appendLine("    val beforeMethods: List<(Any) -> Unit>,")
            appendLine("    val afterMethods: List<(Any) -> Unit>,")
            appendLine("    val testMethods: List<WebJsGeneratedTestMethod>,")
            appendLine(")")
            appendLine()
            appendLine("object WebJsTestSuite {")
            appendLine("    val classes: List<WebJsGeneratedTestClass> = buildList {")

            var classCount = 0
            for (file in candidateFiles) {
                val text = file.readText()
                if (!text.contains("@Test")) continue
                val packageName = packageRegex.find(text)?.groupValues?.get(1) ?: continue
                val className = classRegex.find(text)?.groupValues?.get(1) ?: continue
                val imports = importRegex.findAll(text).associate { match ->
                    val qualifiedName = match.groupValues[1]
                    qualifiedName.substringAfterLast('.') to qualifiedName
                }
                val isParameterized = text.contains("@RunWith(Parameterized::class)")
                    || text.contains("@UseParametersRunnerFactory")
                val pendingAnnotations = mutableListOf<String>()
                val beforeMethods = mutableListOf<String>()
                val afterMethods = mutableListOf<String>()
                val testMethods = mutableListOf<Pair<String, String?>>()
                var parametersMethodName: String? = null

                for (rawLine in text.lineSequence()) {
                    val line = rawLine.trim()
                    if (line.contains("@Before")) pendingAnnotations += "@Before"
                    if (line.contains("@After")) pendingAnnotations += "@After"
                    if (line.contains("@Test")) pendingAnnotations += "@Test"
                    if (line.contains("@Ignore")) pendingAnnotations += "@Ignore"
                    if (line.contains("@Parameters")) pendingAnnotations += "@Parameters"

                    val methodName = methodRegex.find(line)?.groupValues?.get(1)
                    if (methodName != null) {
                        if (pendingAnnotations.any { it == "@Before" }) beforeMethods += methodName
                        if (pendingAnnotations.any { it == "@After" }) afterMethods += methodName
                        if (pendingAnnotations.any { it == "@Parameters" }) parametersMethodName = methodName
                        if (pendingAnnotations.any { it == "@Test" }) {
                            val ignored = pendingAnnotations.any { it == "@Ignore" }
                            testMethods += methodName to if (ignored) "ignored" else null
                        }
                        pendingAnnotations.clear()
                        continue
                    }

                    if (line.isBlank() || line.startsWith("//") || line.startsWith("*") || line.startsWith("/*") || line.startsWith("*/")) {
                        continue
                    }
                    if (!line.startsWith("@")) {
                        pendingAnnotations.clear()
                    }
                }

                if (testMethods.isEmpty()) continue
                classCount++
                val fqn = "$packageName.$className"
                val constructorParamTypes = if (isParameterized) {
                    parsePrimaryConstructorTypes(text, className)
                        .map { qualifyTypeReference(it, imports, packageName) }
                } else emptyList()

                if (isParameterized) {
                    val parameterMethod = parametersMethodName ?: "parameters"
                    appendLine("        addAll(run {")
                    appendLine("            val parameterSets = $fqn.$parameterMethod()")
                    appendLine("            parameterSets.mapIndexed { parameterIndex, parameterValues ->")
                    appendLine("                val args = parameterValues ?: emptyArray()")
                    appendLine("                WebJsGeneratedTestClass(")
                    appendLine("                    className = \"$fqn[${'$'}parameterIndex]\",")
                    appendLine("                    createInstance = {")
                    appendLine("                        require(args.size == ${constructorParamTypes.size}) { \"Expected ${constructorParamTypes.size} constructor arguments for $fqn but received ${'$'}{args.size}\" }")
                    appendLine("                        $fqn(")
                    constructorParamTypes.forEachIndexed { index, type ->
                        appendLine("                            args[$index] as $type,")
                    }
                    appendLine("                        )")
                    appendLine("                    },")
                    appendGeneratedMethodLists(beforeMethods, afterMethods, testMethods, fqn)
                    appendLine("                )")
                    appendLine("            }")
                    appendLine("        })")
                } else {
                    appendLine("        add(")
                    appendLine("            WebJsGeneratedTestClass(")
                    appendLine("                className = \"$fqn\",")
                    appendLine("                createInstance = { $fqn() },")
                    appendGeneratedMethodLists(beforeMethods, afterMethods, testMethods, fqn, "                ")
                    appendLine("            )")
                    appendLine("        )")
                }
            }
            appendLine("    }")
            appendLine("}")
            logger.lifecycle("Generated WebJsTestSuite with $classCount classes at ${outputFile.invariantSeparatorsPath}")
        }
        outputFile.writeText(body)
    }
}

abstract class WebPostProcessDistTask : DefaultTask() {
    @get:Internal
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun postProcess() {
        val outputDirFile = outputDir.get().asFile
        promoteWebappToRoot(outputDirFile)
        stripWebIncompatibleSkinObjects(File(outputDirFile, "assets/Skin.json"))
        hardenIndexBootstrap(File(outputDirFile, "index.html"))
        hardenTeaVisibilityLifecycle(File(outputDirFile, "unciv.js"))
    }
}

private fun stripWebIncompatibleSkinObjects(skinFile: File) {
    if (!skinFile.isFile) return
    val promotionColorsBlock = Regex(
        """(?ms),\s*"com\.unciv\.ui\.screens\.pickerscreens\.PromotionScreenColors"\s*:\s*\{\s*"default"\s*:\s*\{[^{}]*}\s*}"""
    )
    val content = skinFile.readText()
    val updated = content.replace(promotionColorsBlock, "")
    if (updated != content) {
        skinFile.writeText(updated)
    }
}

private fun hardenIndexBootstrap(indexFile: File) {
    if (!indexFile.isFile) return
    var content = indexFile.readText()
    if (!content.contains("rel=\"icon\"")) {
        content = content.replace("<head>", "<head>\n        <link rel=\"icon\" href=\"data:,\">")
    }
    val hardened = """
        <script>
            (function () {
                function boot() {
                    if (window.__uncivBootStarted) return;
                    if (typeof window.main !== 'function') {
                        setTimeout(boot, 25);
                        return;
                    }
                    window.__uncivBootStarted = true;
                    window.main();
                }
                if (document.readyState === 'complete') {
                    setTimeout(boot, 0);
                } else {
                    window.addEventListener('load', boot, { once: true });
                }
            })();
        </script>
    """.trimIndent()
    val legacyRegex = Regex(
        "<script>\\s*async function start\\(\\) \\{\\s*main\\(\\)\\s*}\\s*window.addEventListener\\(\\\"load\\\", start\\);\\s*</script>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    val hasWasmGcBootstrap = content.contains("TeaVM.wasmGC.load") || content.contains("teavm.exports.main(")
    content = if (legacyRegex.containsMatchIn(content)) {
        content.replace(legacyRegex, hardened)
    } else if (!hasWasmGcBootstrap && !content.contains("__uncivBootStarted")) {
        content.replace("</body>", "$hardened\n    </body>")
    } else {
        content
    }
    indexFile.writeText(content)
}

private fun hardenTeaVisibilityLifecycle(jsFile: File) {
    if (!jsFile.isFile) return
    val content = jsFile.readText()
    val startRegex = Regex("""[A-Za-z0-9_]+_TeaApplication\${'$'}2_handleEvent = \(\${'$'}this, \${'$'}evt\) => \{""")
    val endRegex = Regex("""[A-Za-z0-9_]+_TeaApplication\${'$'}2_handleEvent\${'$'}exported\${'$'}0 =""")
    val start = startRegex.find(content)?.range?.first ?: return
    val end = endRegex.find(content, start + 1)?.range?.first ?: return
    val originalBlock = content.substring(start, end)
    val pauseRegex = Regex("""case 11:(\r?\n)(\s+)([A-Za-z0-9${'$'}]+)\.\${'$'}pause\(\);""")
    val resumeRegex = Regex("""case 12:(\r?\n)(\s+)([A-Za-z0-9${'$'}]+)\.\${'$'}resume0\(\);""")
    val pauseMatch = pauseRegex.find(originalBlock) ?: return
    val pauseReplacement = pauseMatch.run {
        val newline = groupValues[1]
        val indent = groupValues[2]
        val variable = groupValues[3]
        "case 11:$newline${indent}if ($variable === null)$newline${indent}    return;$newline${indent}$variable.${'$'}pause();"
    }
    val pausePatchedBlock = originalBlock.replaceRange(pauseMatch.range, pauseReplacement)
    val resumeMatch = resumeRegex.find(pausePatchedBlock) ?: return
    val resumeReplacement = resumeMatch.run {
        val newline = groupValues[1]
        val indent = groupValues[2]
        val variable = groupValues[3]
        "case 12:$newline${indent}if ($variable === null)$newline${indent}    return;$newline${indent}$variable.${'$'}resume0();"
    }
    val patchedBlock = pausePatchedBlock.replaceRange(resumeMatch.range, resumeReplacement)
    if (patchedBlock == originalBlock) return
    jsFile.writeText(content.replaceRange(start, end, patchedBlock))
}

private fun promoteWebappToRoot(outputDir: File) {
    val webappDir = File(outputDir, "webapp")
    if (!webappDir.isDirectory) return
    if (File(outputDir, "index.html").isFile) return
    webappDir.listFiles()?.forEach { child ->
        val target = File(outputDir, child.name)
        if (target.exists()) {
            target.deleteRecursively()
        }
        Files.move(child.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    webappDir.deleteRecursively()
}

private fun StringBuilder.appendGeneratedMethodLists(
    beforeMethods: List<String>,
    afterMethods: List<String>,
    testMethods: List<Pair<String, String?>>,
    fqn: String,
    indent: String = "                    ",
) {
    if (beforeMethods.isEmpty()) {
        appendLine("${indent}beforeMethods = emptyList(),")
    } else {
        appendLine("${indent}beforeMethods = listOf(")
        beforeMethods.forEach { method ->
            appendLine("${indent}    { instance -> (instance as $fqn).$method() },")
        }
        appendLine("${indent}),")
    }

    if (afterMethods.isEmpty()) {
        appendLine("${indent}afterMethods = emptyList(),")
    } else {
        appendLine("${indent}afterMethods = listOf(")
        afterMethods.forEach { method ->
            appendLine("${indent}    { instance -> (instance as $fqn).$method() },")
        }
        appendLine("${indent}),")
    }

    appendLine("${indent}testMethods = listOf(")
    testMethods.forEach { (method, ignoredReason) ->
        val ignoredValue = ignoredReason?.let { "\"$it\"" } ?: "null"
        appendLine("${indent}    WebJsGeneratedTestMethod(\"$method\", $ignoredValue, { instance -> (instance as $fqn).$method() }),")
    }
    appendLine("${indent}),")
}

private fun parsePrimaryConstructorTypes(text: String, className: String): List<String> {
    val classRegex = Regex(
        "class\\s+${Regex.escape(className)}\\s*\\((.*?)\\)\\s*\\{",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    val constructorContent = classRegex.find(text)?.groupValues?.get(1)?.let(::stripComments) ?: return emptyList()
    if (constructorContent.isBlank()) return emptyList()
    return splitTopLevel(constructorContent, ',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull(::extractConstructorParameterType)
}

private fun stripComments(text: String): String {
    return blockCommentRegex.replace(text, "")
        .lineSequence()
        .map { lineCommentRegex.replace(it, "") }
        .joinToString("\n")
}

private fun splitTopLevel(text: String, delimiter: Char): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    for (ch in text) {
        when (ch) {
            '<', '(', '[', '{' -> depth++
            '>', ')', ']', '}' -> depth--
        }
        if (ch == delimiter && depth == 0) {
            parts += current.toString()
            current.clear()
            continue
        }
        current.append(ch)
    }
    if (current.isNotEmpty()) parts += current.toString()
    return parts
}

private fun extractConstructorParameterType(parameter: String): String? {
    val withoutAnnotations = parameter
        .replace(Regex("@[A-Za-z0-9_.]+(?:\\([^)]*\\))?\\s*"), "")
        .trim()
    val colonIndex = withoutAnnotations.indexOf(':')
    if (colonIndex < 0) return null
    return withoutAnnotations.substring(colonIndex + 1)
        .substringBefore("=")
        .trim()
        .takeIf { it.isNotEmpty() }
}

private fun qualifyTypeReference(type: String, imports: Map<String, String>, packageName: String): String {
    return Regex("\\b[A-Z][A-Za-z0-9_]*\\b").replace(type) { match ->
        val simpleName = match.value
        when {
            simpleName in builtinTypeNames -> simpleName
            imports.containsKey(simpleName) -> imports.getValue(simpleName)
            else -> "$packageName.$simpleName"
        }
    }
}
