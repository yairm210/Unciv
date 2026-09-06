package com.unciv.testing

import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm.AStarPathfinding
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm.ClassicPathfinding
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

/**
 *  Factory to be used in a `@UseParametersRunnerFactory` after `@RunWith(Parameterized::class)`
 *
 *  Note `Parameterized` requires a `parameters()` method on the level of the annotated class, which in kotlin can _only_ be
 *  satisfied by a `@JvmStatic` inside a companion - with that, the actual companion is still a static named "Companion" and still carries
 *  `TestClass.Companion.parameters()`, but the compiler adds a second call path - a JVM-visible `TestClass.parameters()` which is what `Parameterized` needs.
 */
class TestRunnerFactory : ParametersRunnerFactory {
    /**
     * Marks a _parameterized_ test class as needing the Gdx headless application loop (GL context,
     * Gdx-thread dispatchers) while its tests run. Only classes carrying this get routed to
     * [GdxTestRunner] by [TestRunnerFactory]; everything else uses the cheaper [BaseTestRunner].
     */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    annotation class WithGdx

    override fun createRunnerForTestWithParameters(testConfig: TestWithParameters?): BaseTestRunner {
        val testClass = testConfig?.testClass?.javaClass
        return if (testClass?.getAnnotation(WithGdx::class.java) != null)
            GdxTestRunner(testClass, testConfig)
        else
            BaseTestRunner(testClass, testConfig)
    }

    /** Common parameter sets for parameterized tests */
    object Parameters {
        /** Execute a parameterized test with both pathfinding implementations */
        val pathfinding: Collection<Array<Any?>?> = listOf(arrayOf(ClassicPathfinding), arrayOf(AStarPathfinding))
    }
}
