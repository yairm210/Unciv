package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.UncivScriptEvaluationConfiguration
import com.unciv.models.ruleset.Script
import com.unciv.models.ruleset.unique.Unique
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.utils.Log
import org.jetbrains.kotlin.utils.KotlinPaths
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.reflect.KClass
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.enableScriptsInstancesSharing
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.resultField
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleInMemory
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.copyWithoutModule
import kotlin.script.experimental.jvm.impl.scriptMetadataPath
import kotlin.script.experimental.jvm.impl.toBytes
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrNull
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.saveToJar

class UncivHost : BasicJvmScriptingHost()

/**
 * Data class for providing data to scripts. These are all defined relative to each specific script case.
 * For usage notes, see comments in [UncivScript].companion.
 * @param name: The name that the script will use to reference the data. This is what script makers
 * will use to reference the variable.
 * @param type: The [KClass] of the data.
 * @param value: The actual data. These are always val so cannot be reassigned but non-primitives may
 * have their fields modified.
 * @param nullable: This is true if this data in this instance is nullable (.kt file definition of
 * this variable has ? after it). Set true if this is the case otherwise false.
 */
data class ProvidedProperty(val name: String, val type: KClass<*>, val value: Any?, val nullable: Boolean)

class UncivScript {
    companion object {
        operator fun invoke() = UncivScript()
        val defaultProvidedProperty: ArrayList<ProvidedProperty> = arrayListOf(ProvidedProperty("additionalErrors", ArrayList::class, arrayListOf<ScriptDiagnostic>(), false))
        val forbiddenStrings: ArrayList<String> = arrayListOf("java.io.File")
        fun errorReportCopiedToast(screen: BaseScreen): ToastPopup =
                ToastPopup("Error report copied.", screen)

        /**
         * Helper functions for creating [ProvidedProperty] which are given to scripts as a means to
         * pass game objects to them. If you are creating code that can call a script, use these.
         * Scripts will reference these variables in code by their [name] as variables and not strings.
         * For example take the code
         *
         * mapUnitProperty(foo, false, "mapUnitNamedFoo")
         *
         * If we wanted to manipulate this object in a .kt file, we'd normally write code like
         *
         * val ownerString: String = foo.owner
         *
         * but a script will see the same [MapUnit] object named as `mapUnitNamedFoo` so the following
         * .unciv.kts code would be valid:
         *
         * val currentOwner: String = mapUnitNamedFoo.owner
         * mapUnitNamedFoo.owner = "bar"
         *
         * The above code would look at the Kotlin object named `foo`'s field `owner` then change it
         * to a string "bar" which will result in the actual [MapUnit.owner] field being changed
         * to "bar" even after the script finishes running. Objects are passed as val so users will
         * be able to modify game objects' fields at will (except primitives like [String]s or [Int]).
         *
         * The [nullable] parameter must be set to true if [instance] is nullable (has a `?` after its
         * class). Failure to do this will result in a script not running and not throwing an error
         * either which will give you a headache trying to debug it.
         *
         * For example, if you are trying to pass a variable `foo: Int` to a script, nullable will be false.
         * But if you are trying to pass a variable `bar: GameInfo?` to a script, nullable needs to
         * be set to true even if you are giving it a non-null instance of GameInfo in this particular case.
         */
        fun booleanProperty(instance: Boolean?, nullable: Boolean, name: String) =
                ProvidedProperty(name, Boolean::class, instance, nullable)
        fun intProperty(instance: Int?, nullable: Boolean, name: String) =
                ProvidedProperty(name, Int::class, instance, nullable)
        fun floatProperty(instance: Float?, nullable: Boolean, name: String) =
                ProvidedProperty(name, Float::class, instance, nullable)
        fun stringProperty(instance: String?, nullable: Boolean, name: String) =
                ProvidedProperty(name, String::class, instance, nullable)

        fun gameInfoProperty(instance: GameInfo? = UncivGame.Current.gameInfo, nullable: Boolean, name: String = "gameInfo") =
                ProvidedProperty(name, GameInfo::class, instance, nullable)
        fun civilizationProperty(instance: Civilization?, nullable: Boolean, name: String = "civilization") =
                ProvidedProperty(name, Civilization::class, instance, nullable)
        fun mapUnitProperty(instance: MapUnit?, nullable: Boolean, name: String = "mapUnit") =
                ProvidedProperty(name, MapUnit::class, instance, nullable)
        fun cityProperty(instance: City?, nullable: Boolean, name: String = "city") =
                ProvidedProperty(name, City::class, instance, nullable)
        fun tileProperty(instance: Tile?, nullable: Boolean, name: String = "tile") =
                ProvidedProperty(name, Tile::class, instance, nullable)
        fun uniqueProperty(instance: Unique?, nullable: Boolean, name: String = "unique") =
                ProvidedProperty(name, Unique::class, instance, nullable)
    }

    private fun getScriptDefinition(scriptFile: File, args: List<ProvidedProperty>): ScriptCompilationConfiguration? {
        if (forbiddenStrings.any { scriptFile.readText().contains(it) }) {
            return null
        }
        return createJvmCompilationConfigurationFromTemplate<Script> {
            resultField("")
            providedProperties(*(args.map { it.name to KotlinType(it.type, it.nullable) }.toTypedArray()))
            hostConfiguration(ScriptingHostConfiguration {
                jvm {
                    compilationCache(
                        UncivCompiledScriptJarsCache { _, _ ->
                            File(scriptFile.absolutePath + ".jar")
                        }
                    )
                }
            })
        }
    }

    suspend fun compile(scriptFile: FileScriptSource, args: ArrayList<ProvidedProperty>): ResultWithDiagnostics<CompiledScript> {
        @Suppress("UNCHECKED_CAST")
        (defaultProvidedProperty[0].value as ArrayList<ScriptDiagnostic>).clear()
        args.addAll(defaultProvidedProperty)
        val scriptDefinition = getScriptDefinition(scriptFile.file, args)
            ?: return ResultWithDiagnostics.Failure(ScriptDiagnostic(ScriptDiagnostic.unspecifiedException, ""))
        return UncivHost().compiler(scriptFile, scriptDefinition)
    }

    /**
     * Used to mass compile scripts.
     */
    /*suspend fun compileScripts(scriptFiles: List<File>, args: ArrayList<ProvidedProperty>): ResultWithDiagnostics<CompiledScript>? {
        for (file in scriptFiles) {
            @Suppress("UNCHECKED_CAST")
            (defaultProvidedProperty[0].value as ArrayList<ScriptDiagnostic>).clear()
            args.addAll(defaultProvidedProperty)
            val scriptDefinition = getScriptDefinition(scriptFile, args)
                ?: return ResultWithDiagnostics.Failure()
            var ret: ResultWithDiagnostics<CompiledScript>? = null
            ret = UncivHost().compiler.invoke(scriptFile.toScriptSource(), scriptDefinition)
            return ret
        }
    }*/

    fun execute(scriptSource: FileScriptSource, args: ArrayList<ProvidedProperty>): ResultWithDiagnostics<EvaluationResult> {
        val scriptDefinition = getScriptDefinition(scriptSource.file, args)
            ?: return ResultWithDiagnostics.Failure()
        val evaluationEnv = UncivScriptEvaluationConfiguration.with {
            jvm {
                loadDependencies(true)
                baseClassLoader((Script::class).java.classLoader)
            }

            //providedProperties(*(args.map { it.name to if (it.value is String) it.value as? java.lang.String else it.value }.toTypedArray()))
            providedProperties(*(args.map { it.name to it.value }.toTypedArray()))
            enableScriptsInstancesSharing()
        }

        val hostInstance = UncivHost()
        // TODO break up into compile and eval modes to separate compiler errors from execution errors
        return hostInstance.eval(scriptSource, scriptDefinition, evaluationEnv)
    }

    fun runScript(path: String, vararg args: ProvidedProperty, screen: BaseScreen? = null, viewingCiv: Civilization? = null) {
        @Suppress("UNCHECKED_CAST")
        val additionalErrors = (defaultProvidedProperty[0].value as ArrayList<ScriptDiagnostic>?)!!
        additionalErrors.clear()
        val argArray = arrayListOf(*args)

        val checkedPath = path + if (!path.endsWith(".kts")) ".unciv.kts" else ""
        val scriptFile = File(checkedPath)
        val res: ResultWithDiagnostics<EvaluationResult>?

        // check if this is a valid filepath
        if (!scriptFile.isFile) {
            // not a valid file path
            res = ResultWithDiagnostics.Failure(ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, "No script found at $path", ScriptDiagnostic.Severity.ERROR))
            reportErrorsToUser(checkedPath, res.reports, screen, viewingCiv)
            return
        }
        val scriptText = try {
            scriptFile.readText()
        } catch (e: Throwable) {
            println(e.stackTraceToString())
            // can't be read, return for now
            return
        }
        val functionName = "main" // TODO customizable function names (something like user specifies that a particular use case should reference file.unciv.kts:foo where foo is the function and file.unciv.kts is the file to look into)
        var argString = ""
        for (arg in args) {
            argString += arg.name + ","
        }
        argString = argString.dropLast(1)
        argArray.addAll(defaultProvidedProperty)

        val preparedScript = prepareScript(scriptText, functionName, argString)
        val scriptSource = FileScriptSource(scriptFile, preparedScript)
        res = try {
            execute(scriptSource, argArray)
        } catch (e: Throwable) {
            // exception during the script execution most likely at the .kt code level (this file or
            // JVM/kotlin script host, should probably try to recover and let the user know what happened
            // TODO
            null
        }

        // error reporting
        val combinedReports: MutableList<ScriptDiagnostic> = res?.reports?.toMutableList()
            ?: mutableListOf()
        combinedReports.addAll(additionalErrors)
        val filteredResponses = filterResponses(combinedReports)
        if (filteredResponses?.any { it.severity != ScriptDiagnostic.Severity.INFO && it.severity != ScriptDiagnostic.Severity.DEBUG } == true) {
            reportErrorsToUser(checkedPath, filteredResponses, screen, viewingCiv)
        }
    }

    private fun prepareScript(originalScript: String, functionName: String, argString: String): String {
        return originalScript + """
            try {
                ${functionName}(${argString})
            } catch (e: Throwable) {
                @Suppress("UNCHECKED_CAST")
                (additionalErrors as ArrayList<ScriptDiagnostic>).add(ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, e.stackTraceToString(), ScriptDiagnostic.Severity.ERROR))
            }
        """.trimIndent()
    }

    private fun reportErrorsToUser(scriptPath: String,
                                   filteredResponses: List<ScriptDiagnostic>,
                                   screen: BaseScreen? = null,
                                   viewingCiv: Civilization? = null
    ) {
        var responseString = ""
        for (element in filteredResponses) {
            responseString += element.toString() + "\n"
        }

        val popupScreen = screen
                ?: UncivGame.Current.screen

        println(responseString)
        if (viewingCiv != null) {
            viewingCiv.popupAlerts.add(PopupAlert(AlertType.ScriptOutput, responseString))
        } else if (popupScreen != null) {
            // this is used for scripts run outside of the main
            ConfirmPopup(
                popupScreen,
                "{Script output}:\n\n${responseString}\n{in}\n${scriptPath}",
                "Copy contents to clipboard",
                true
            ) {
                Gdx.app.clipboard.contents = responseString
                errorReportCopiedToast(popupScreen)
            }.open()
        }
    }

    private fun filterResponses(responses: List<ScriptDiagnostic>?,
                                minimumReportThreshold: ScriptDiagnostic.Severity = ScriptDiagnostic.Severity.ERROR
    ): List<ScriptDiagnostic>? {
        if (responses == null) return null

        return responses
            //.filterNot { it.message.startsWith("Using new") }
            .filter { it.severity >= minimumReportThreshold }
            .sortedByDescending { it.severity }
    }
}

/**
 * Inherits [CompiledScriptJarsCache] to modify caching so we can save in the jar manifest then check
 * Unciv version for any potential incompatibilities.
 */
class UncivCompiledScriptJarsCache(scriptToFile: (SourceCode, ScriptCompilationConfiguration) -> File?) :
    CompiledScriptJarsCache(scriptToFile) {

    override fun get(script: SourceCode, scriptCompilationConfiguration: ScriptCompilationConfiguration): CompiledScript? {
        val compiledScript = super.get(script, scriptCompilationConfiguration)
            ?: return null

        val jarFile = scriptToFile(script, scriptCompilationConfiguration)!!
        val sourceLastModified = File(script.locationId!!).lastModified()
        if (sourceLastModified > jarFile.lastModified() + 1000L) {
            return null // out of date so recompile
        }
        val uncivVersion = jarFile.inputStream().use { ostr ->
            JarInputStream(ostr).use {
                (it.manifest.mainAttributes.getValue("Unciv-Version") ?: "")
            }
        }
        // invalid
        if (uncivVersion == "") return null

        Log.debug("Loaded a mod script ${jarFile.path} compiled in Unciv version $uncivVersion")

        // in the future, we can do version checking here if we want to overhaul some sort of script
        // mechanic and force everyone to recompile

        return compiledScript
    }

    override fun store(
        compiledScript: CompiledScript,
        script: SourceCode,
        scriptCompilationConfiguration: ScriptCompilationConfiguration
    ) {
        val file = scriptToFile(script, scriptCompilationConfiguration)
            ?: throw IllegalArgumentException("Unable to find a mapping to a file for the script $script")

        val jvmScript = (compiledScript as? KJvmCompiledScript)
            ?: throw IllegalArgumentException("Unsupported script type ${compiledScript::class.java.name}")

        saveToJar(file, jvmScript)
    }

    /**
     * This is entirely copy-pasted from [KJvmCompiledScript.saveToJar] with 1 line change so we can
     * save the Unciv version the JAR was compiled with.
     */
    private fun saveToJar(outputJar: File, script: KJvmCompiledScript) {
        val module = (script.getCompiledModule() as? KJvmCompiledModuleInMemory)
            ?: throw IllegalArgumentException("Unsupported module type ${script.getCompiledModule()}")
        val dependenciesFromScript = script.compilationConfiguration[ScriptCompilationConfiguration.dependencies]
            ?.filterIsInstance<JvmDependency>()
            ?.flatMap { it.classpath }
            .orEmpty()
        val dependenciesForMain = scriptCompilationClasspathFromContextOrNull(
            KotlinPaths.Jar.ScriptingLib.baseName, KotlinPaths.Jar.ScriptingJvmLib.baseName,
            classLoader = this::class.java.classLoader,
            wholeClasspath = false
        ) ?: emptyList()
        // saving only existing files, so the check for the existence in the loadScriptFromJar is meaningful
        val dependencies = (dependenciesFromScript + dependenciesForMain).distinct().filter { it.exists() }
        FileOutputStream(outputJar).use { fileStream ->
            val manifest = Manifest()
            manifest.mainAttributes.apply {
                putValue("Manifest-Version", "1.0")
                putValue("Created-By", "JetBrains Kotlin")
                if (dependencies.isNotEmpty()) {
                    putValue("Class-Path", dependencies.joinToString(" ") { it.toURI().toURL().toExternalForm() })
                }
                putValue("Main-Class", script.scriptClassFQName)
                putValue("Unciv-Version", UncivGame.VERSION.text)
            }
            JarOutputStream(fileStream, manifest).use { jarStream ->
                jarStream.putNextEntry(JarEntry(scriptMetadataPath(script.scriptClassFQName)))
                jarStream.write(script.copyWithoutModule().toBytes())
                jarStream.closeEntry()
                for ((path, bytes) in module.compilerOutputFiles) {
                    jarStream.putNextEntry(JarEntry(path))
                    jarStream.write(bytes)
                    jarStream.closeEntry()
                }
                jarStream.finish()
                jarStream.flush()
            }
            fileStream.flush()
        }
    }
}
