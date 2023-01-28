package com.unciv.models.ruleset

import java.io.File
import java.net.JarURLConnection
import java.net.URL
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.RefineScriptCompilationConfigurationHandler
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.foundAnnotations
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.api.scriptsInstancesSharing
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource

@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
annotation class Import(vararg val paths: String)

@KotlinScript(
    fileExtension = "unciv.kts",
    compilationConfiguration = UncivScriptConfiguration::class,
    evaluationConfiguration = UncivScriptEvaluationConfiguration::class
)
abstract class Script

class UncivScriptConfiguration : ScriptCompilationConfiguration()

internal fun URL.toContainingJarOrNull(): File? =
        if (protocol == "jar") {
            (openConnection() as? JarURLConnection)?.jarFileURL?.toFileOrNull()
        } else null

internal fun URL.toFileOrNull() =
        try {
            File(toURI())
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: java.net.URISyntaxException) {
            null
        } ?: run {
            if (protocol != "file") null
            else File(file)
        }

object UncivScriptEvaluationConfiguration : ScriptEvaluationConfiguration(
    {
        scriptsInstancesSharing(true)
    }
)

class AnnotationProcessor : RefineScriptCompilationConfigurationHandler {
    override fun invoke(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> =
            processAnnotations(context)

    private fun processAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.foundAnnotations)?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()

        val scriptBaseDir = (context.script as? FileBasedScriptSource)?.file?.parentFile
        val importedSources = annotations.flatMap {
            (it as? Import)?.paths?.map {
                sourceName -> FileScriptSource(scriptBaseDir?.resolve(sourceName) ?: File(sourceName))
            } ?: emptyList()
        }

        for (script in importedSources) {
            if (script.text.contains("java.io.File", true)) {
                println("Script contains io.File!")
                return ResultWithDiagnostics.Failure(
                    ScriptDiagnostic(ScriptDiagnostic.unspecifiedError,
                        "Imported script ${script.file} contains a forbidden library import",
                        ScriptDiagnostic.Severity.ERROR)
                )
            }
        }

        return ScriptCompilationConfiguration(context.compilationConfiguration) {
            if (importedSources.isNotEmpty()) importScripts.append(importedSources)
        }.asSuccess()
    }
}
