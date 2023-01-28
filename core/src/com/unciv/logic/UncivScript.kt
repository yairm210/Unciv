package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.AnnotationProcessor
import com.unciv.models.ruleset.Import
import com.unciv.models.ruleset.UncivScriptEvaluationConfiguration
import com.unciv.models.ruleset.Script
import com.unciv.models.ruleset.UncivScriptConfiguration
import com.unciv.models.ruleset.toContainingJarOrNull
import com.unciv.models.ruleset.unique.Unique
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.utils.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.reflect.KClass
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.enableScriptsInstancesSharing
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.resultField
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleInMemory
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.copyWithoutModule
import kotlin.script.experimental.jvm.impl.createScriptFromClassLoader
import kotlin.script.experimental.jvm.impl.scriptMetadataPath
import kotlin.script.experimental.jvm.impl.toBytes
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrNull
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.jsr223.importAllBindings
import kotlin.script.experimental.jvmhost.jsr223.jsr223
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

class UncivScriptSource(val gdxFile: FileHandle, override val text: String) : FileScriptSource(gdxFile.file(), text)

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

    private fun getScriptDefinition(sourceCode: UncivScriptSource, args: List<ProvidedProperty>): ScriptCompilationConfiguration? {
        if (forbiddenStrings.any { sourceCode.text.contains(it) }) {
            return null
        }
        return createJvmCompilationConfigurationFromTemplate<Script> {
            defaultImports(Import::class, ScriptDiagnostic::class)
            resultField("")
            jvm {
                val keyResource = UncivScriptConfiguration::class.java.name.replace('.', '/') + ".class"
                val thisJarFile = UncivScriptConfiguration::class.java.classLoader.getResource(keyResource)?.toContainingJarOrNull()
                if (thisJarFile != null) {
                    dependenciesFromClassContext(
                        UncivScriptConfiguration::class,
                        thisJarFile.name,
                        wholeClasspath = true
                    )
                } else {
                    dependenciesFromClassContext(UncivScriptConfiguration::class, wholeClasspath = true)
                }
            }
            refineConfiguration {
                onAnnotations(Import::class, handler = AnnotationProcessor())
            }

            ide {
                acceptedLocations(ScriptAcceptedLocation.Everywhere)
            }

            jsr223 {
                importAllBindings(true)
            }
            providedProperties(*(args.map { it.name to KotlinType(it.type, it.nullable) }.toTypedArray()))
            hostConfiguration(ScriptingHostConfiguration {
                jvm {
                    compilationCache(
                        UncivCompiledScriptJarsCache { _, _ ->
                            Gdx.files.local(sourceCode.gdxFile.path() + ".jar").file()
                        }
                    )
                }
            })
        }
    }

    suspend fun compile(scriptFile: UncivScriptSource, args: ArrayList<ProvidedProperty>): ResultWithDiagnostics<CompiledScript> {
        @Suppress("UNCHECKED_CAST")
        (defaultProvidedProperty[0].value as ArrayList<ScriptDiagnostic>).clear()
        args.addAll(defaultProvidedProperty)
        val scriptDefinition = getScriptDefinition(scriptFile, args)
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

    fun execute(scriptSource: UncivScriptSource, args: ArrayList<ProvidedProperty>): ResultWithDiagnostics<EvaluationResult> {
        val scriptDefinition = getScriptDefinition(scriptSource, args)
            ?: return ResultWithDiagnostics.Failure()
        val evaluationEnv = UncivScriptEvaluationConfiguration.with {
            jvm {
                loadDependencies(true)
                baseClassLoader((Script::class).java.classLoader)
            }

            providedProperties(*(args.map { it.name to it.value }.toTypedArray()))
            enableScriptsInstancesSharing()
        }

        val hostInstance = UncivHost()
        // TODO break up into compile and eval modes to separate compiler errors from execution errors
        val cache = scriptDefinition[ScriptCompilationConfiguration.hostConfiguration]?.get(ScriptingHostConfiguration.jvm.compilationCache) as UncivCompiledScriptJarsCache

        val cached = cache.get(scriptSource, scriptDefinition)

        if (cached != null) {
            return hostInstance.runInCoroutineContext {
                hostInstance.evaluator(cached, evaluationEnv)
            }
        }
        return hostInstance.eval(scriptSource, scriptDefinition, evaluationEnv)
    }

    fun runScript(path: String, vararg args: ProvidedProperty, screen: BaseScreen? = null, viewingCiv: Civilization? = null) {
        @Suppress("UNCHECKED_CAST")
        val additionalErrors = (defaultProvidedProperty[0].value as ArrayList<ScriptDiagnostic>?)!!
        additionalErrors.clear()
        val argArray = arrayListOf(*args)

        val checkedPath = path.removePrefix("/") + if (!path.endsWith(".kts")) ".unciv.kts" else ""
        val scriptFile = Gdx.files.local(checkedPath)//.list().first { it.file().name.startsWith(path.split("/").last()) && !it.file().name.endsWith(".jar") && !it.file().name.endsWith(".dex") }
        val res: ResultWithDiagnostics<EvaluationResult>?

        // check if this is a valid filepath
        if (!scriptFile.file().exists()) {
            res = ResultWithDiagnostics.Failure(ScriptDiagnostic(ScriptDiagnostic.unspecifiedError,
                "No script found at $checkedPath (${scriptFile.file().absolutePath})\n(${Gdx.files.local("scripts/").list().map { it.name() }})",
                ScriptDiagnostic.Severity.ERROR))
            reportErrorsToUser(checkedPath, res.reports, screen, viewingCiv)
            return
        }
        val scriptText = try {
            scriptFile.readString()
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
        val scriptSource = UncivScriptSource(scriptFile, preparedScript)
        res = try {
            execute(scriptSource, argArray)
        } catch (e: Throwable) {
            // exception during the script execution most likely at the .kt code level (this file or
            // JVM/kotlin script host, should probably try to recover and let the user know what happened
            // TODO
            println(e.stackTraceToString())
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

    /**
     * This is a work around that in my opinion is extremely useful for at least 3 reasons.
     * 1) The biggest reason is that it allows use to nearly guarantee that error logging makes its
     * way into every user-made script and that any exceptions will get directed through the game so
     * they can be displayed to users. This includes specific line numbers of exceptions that will
     * make debugging less painful for mod makers.
     * 2) It forces users to create a main function that will be called, and this function needs its
     * parameters defined. This helps both the user understand the variables available for the script
     * and linting software to know what these variables are which only benefits users.
     * 3) Kotlin scripts do not natively have a means to call a specific method in a script and only
     * that one function. By appending a function call in the last line of a script, we can simulate
     * a specific function call so that users can have multiple scripts placed in the same script file
     * and the game will understand it as such.
     */
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
            // this is used for scripts run outside of the main game if any exist
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
                                minimumReportThreshold: ScriptDiagnostic.Severity = ScriptDiagnostic.Severity.WARNING
    ): List<ScriptDiagnostic>? {
        if (responses == null) return null

        return responses
            .filterNot { it.message.startsWith("Using new faster version of JAR FS") } // generic compiler warning
            .filter { it.severity >= minimumReportThreshold }
            .sortedByDescending { it.severity }
    }
}

/**
 * Inherits [CompiledScriptJarsCache] to modify caching so we can save in the jar manifest then check
 * Unciv version for any potential incompatibilities.
 */
@Suppress("UNCHECKED_CAST")
class UncivCompiledScriptJarsCache(scriptToFile: (UncivScriptSource, ScriptCompilationConfiguration) -> File?) :
    CompiledScriptJarsCache(scriptToFile as (SourceCode, ScriptCompilationConfiguration) -> File?) {

    fun get(script: UncivScriptSource, scriptCompilationConfiguration: ScriptCompilationConfiguration): CompiledScript? {
        // load JAR archive
        val scriptArchive = scriptToFile(script, scriptCompilationConfiguration)
            ?: throw IllegalArgumentException("Unable to find a mapping to a file for the script $script")

        if (!scriptArchive.exists()) return null
        val loadedScript = scriptArchive.loadScriptFromJar()
            ?: return null

        val sourceLastModified = script.gdxFile.lastModified()//File(script.locationId!!).lastModified()
        val jarLastModified = scriptArchive.lastModified()
        if (sourceLastModified > jarLastModified + 1000L) {
            return null // out of date so recompile
        }
        val uncivVersion = scriptArchive.inputStream().use { ostr ->
            JarInputStream(ostr).use {
                (it.manifest.mainAttributes.getValue("Unciv-Version") ?: "")
            }
        }
        // invalidate if not compiled with an Unciv version
        if (uncivVersion == "") return null

        Log.debug("Loaded a mod script ${scriptArchive.path} compiled in Unciv version $uncivVersion")

        // in the future, we can do version checking here if we want to overhaul some sort of script
        // mechanic and force everyone to recompile

        return loadedScript
    }

    /** Nearly replicates [File.loadScriptFromJar] */
    private fun File.loadScriptFromJar(checkMissingDependencies: Boolean = false): CompiledScript? {
        val (className: String?, classPathUrls) = this.inputStream().use { ostr ->
            JarInputStream(ostr).use {
                it.manifest.mainAttributes.getValue("Main-Class") to
                        (it.manifest.mainAttributes.getValue("Class-Path")?.split(" ") ?: emptyList())
            }
        }
        if (className == null) return null

        val classPath = classPathUrls.map { File(Gdx.files.localStoragePath + it).toURI().toURL().toExternalForm() }
            .mapNotNullTo(mutableListOf(this)) { cpEntry ->
            File(URI(cpEntry)).takeIf { it.exists() } ?: File(cpEntry).takeIf { it.exists() }
        }
        classPath.add(this)
        // TODO sort this out (probably just remove if statement)
        return if (!checkMissingDependencies || classPathUrls.size == classPath.size) {
            UncivCompiledScriptLazilyLoaded(className, classPath)
        } else {
            null
        }
    }

    /**
     * This requires the embeddable compiler to function (KotlinPaths).
     */
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
     * This is entirely copy-pasted from [KJvmCompiledScript.saveToJar] with 2 lines change so we can
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
            "kotlin-scripting-common", "kotlin-scripting-jvm", /** these names are extracted from [KotlinPaths] since this file is in the embeddable compiler package */
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
                    putValue("Class-Path", dependencies.joinToString(" ") { outputClassPath(it.path) }) // original code is { it.toURI().toURL().toExternalForm() }
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

    /**
     * @param rawClasspath: class path pointing to a specific path on the user's machine
     * @return truncated path that only references the library JARs
     * TODO needed?
     */
    private fun outputClassPath(rawClasspath: String): String {
        val split = rawClasspath.split("/")
        return if (split.last().endsWith(".jar")) {
            split.last() // jar
        } else {
            ""
        }
    }

    /**
     * Copied from [KJvmCompiledScriptLazilyLoadedFromClasspath] so we can swap out the class loader (could baseClassLoader be set in the eval configuration instead? TODO)
     */
    private class UncivCompiledScriptLazilyLoaded(private val scriptClassFQName: String,
                                                  private val classPath: List<File>
    ) : CompiledScript {
        private var loadedScript: KJvmCompiledScript? = null

        fun getScriptOrError(): KJvmCompiledScript = loadedScript ?: throw RuntimeException("Compiled script is not loaded yet")

        override suspend fun getClass(scriptEvaluationConfiguration: ScriptEvaluationConfiguration?): ResultWithDiagnostics<KClass<*>> {
            if (loadedScript == null) {
                val actualEvaluationConfiguration = scriptEvaluationConfiguration ?: ScriptEvaluationConfiguration()
                val baseClassLoader = actualEvaluationConfiguration[ScriptEvaluationConfiguration.jvm.baseClassLoader]
                val loadDependencies = actualEvaluationConfiguration[ScriptEvaluationConfiguration.jvm.loadDependencies]!!
                val classLoader = UncivGame.Current.platformSpecificHelper?.getClassLoader(
                    classPath.let { if (loadDependencies) it else it.take(1) }.map { it.toURI().toURL() }.toTypedArray(),
                    baseClassLoader
                )
                    ?: return getScriptOrError().getClass(scriptEvaluationConfiguration)

                loadedScript = createScriptFromClassLoader(scriptClassFQName, classLoader)
            }
            return getScriptOrError().getClass(scriptEvaluationConfiguration)
        }

        override val compilationConfiguration: ScriptCompilationConfiguration
            get() = getScriptOrError().compilationConfiguration

        override val sourceLocationId: String?
            get() = getScriptOrError().sourceLocationId

        override val otherScripts: List<CompiledScript>
            get() = getScriptOrError().otherScripts

        override val resultField: Pair<String, KotlinType>?
            get() = getScriptOrError().resultField
    }
}
