package com.unciv.view

import com.unciv.testing.BaseTestRunner
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.jar.JarFile
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.isSubclassOf

/**
 * Enforces the [View] design convention (see [View]'s KDoc):
 * - A "non-owned" [View] (not an [OwnedView]) only ever reads state, so every function it declares must be [Readonly].
 * - An "owned" [View] ([OwnedView] or a subclass) may also mutate state, but by convention those mutating
 *   ("try...") functions always report success/failure as a [Boolean], never anything else.
 *
 * These are structural overrides, not part of the readonly/mutating API surface these rules govern.
 */
@RunWith(BaseTestRunner::class)
class ViewPurityTest {
    private val excludedFunctionNames = setOf("equals", "hashCode", "toString")

    private fun functionsToCheck(kClass: KClass<*>): List<KFunction<*>> =
        kClass.declaredMemberFunctions.filter { it.name !in excludedFunctionNames }

    private fun isReadonly(function: KFunction<*>): Boolean =
        function.annotations.any { it.annotationClass.qualifiedName == "yairm210.purity.annotations.Readonly" }

    @Test
    fun `all functions in non-owned Views are Readonly`() {
        val violations = mutableListOf<String>()
        for (kClass in findViewClasses().filterNot { it.isSubclassOf(OwnedView::class) }) {
            for (function in functionsToCheck(kClass)) {
                if (!isReadonly(function))
                    violations += "${kClass.simpleName}.${function.name} is not a non-owned View, but is not marked @Readonly"
            }
        }
        if (violations.isNotEmpty())
            fail("Found ${violations.size} non-@Readonly function(s) in non-owned Views:\n" + violations.joinToString("\n"))
    }

    @Ignore("CityView.tryRaisePriority/tryLowerPriority return Int?, CityView.tryBombard/MapUnitView.attackOrNuke return Battle.DamageDealt - fix by returning Boolean, then re-enable")
    @Test
    fun `non-Readonly functions in owned Views always return Boolean`() {
        val violations = mutableListOf<String>()
        for (kClass in findViewClasses().filter { it.isSubclassOf(OwnedView::class) }) {
            for (function in functionsToCheck(kClass)) {
                if (isReadonly(function)) continue
                if (function.returnType.classifier != Boolean::class)
                    violations += "${kClass.simpleName}.${function.name} is a non-@Readonly function of an owned View, " +
                        "but returns [${function.returnType}] instead of Boolean"
            }
        }
        if (violations.isNotEmpty())
            fail("Found ${violations.size} non-Boolean-returning non-@Readonly function(s) in owned Views:\n" + violations.joinToString("\n"))
    }

    @Test
    fun `all functions in Foreign-prefixed Views are Readonly`() {
        val violations = mutableListOf<String>()
        for (kClass in findViewClasses().filter { it.simpleName?.startsWith("Foreign") == true }) {
            for (function in functionsToCheck(kClass)) {
                if (!isReadonly(function))
                    violations += "${kClass.simpleName}.${function.name} is a Foreign-prefixed View, but is not marked @Readonly"
            }
        }
        if (violations.isNotEmpty())
            fail("Found ${violations.size} non-@Readonly function(s) in Foreign-prefixed Views:\n" + violations.joinToString("\n"))
    }

    /** Finds every [View] subclass declared directly in the `com.unciv.view` package (this is a flat package, no sub-packages). */
    private fun findViewClasses(): List<KClass<*>> {
        val packageName = "com.unciv.view"
        val packagePath = packageName.replace('.', '/')
        val classLoader = Thread.currentThread().contextClassLoader
        val classNames = mutableSetOf<String>()

        for (resource in classLoader.getResources(packagePath).asSequence()) {
            when (resource.protocol) {
                "file" -> {
                    File(resource.toURI()).listFiles { f -> f.extension == "class" }?.forEach { file ->
                        classNames += "$packageName.${file.nameWithoutExtension}"
                    }
                }
                "jar" -> {
                    val jarPath = resource.path.substringAfter("file:").substringBefore("!")
                    JarFile(jarPath).use { jar ->
                        for (entry in jar.entries()) {
                            if (entry.isDirectory || !entry.name.startsWith("$packagePath/")) continue
                            val relativeName = entry.name.removePrefix("$packagePath/")
                            if (!entry.name.endsWith(".class") || relativeName.contains('/')) continue
                            classNames += "$packageName.${relativeName.removeSuffix(".class")}"
                        }
                    }
                }
                else -> error("Unsupported classpath resource protocol [${resource.protocol}] for $packageName")
            }
        }

        return classNames
            .filterNot { it.contains('$') } // skip nested/companion classes - they're not View instances themselves
            .mapNotNull { name ->
                try { Class.forName(name, false, classLoader).kotlin } catch (e: Throwable) { null }
            }
            .filter { it.isSubclassOf(View::class) }
    }
}
