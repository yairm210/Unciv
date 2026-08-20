package com.unciv.testing

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessFiles
import com.unciv.testing.MemorySnaphots.toMB
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.TestWithParameters
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream

/**
 * Base JUnit4 runner providing the framework's custom per-test annotations ([RedirectOutput], [MeasureDuration], [MeasureMemory])
 * plus parameterized-test constructor support.
 *
 * #### Usage notes
 * - Runs tests directly on the calling thread. Use this unless a test needs [Gdx.app], [Gdx.input], [Gdx.graphics],
 *   [Gdx.audio], an active Gdx GL context, or Gdx-thread dispatcher - for that, use [GdxTestRunner]. [Gdx.files] **is** supported.
 * - Since [Gdx] is a global static, you may inadvertently run on mock fields left by an earlier [GdxTestRunner]. This introduces dependencies on execution order,
 *   JUnit updates, or CI actions forcing a fresh JVM per test - make sure to test your test class stand-alone using the green button in the gutter.
 * - [Gdx.app] Watch for calls of the `.type()` method.
 * - [Gdx.audio] should be harmless when using [TestGame], as it sets all volumes to 0, and the controllers should early-exit.
 * - [Gdx.graphics] is used when saving settings or adding ruleset icons to the font, so you would need [GdxTestRunner] anyway.
 * - [Gdx.input] should only be needed if a test creates Widgets or triggers a setScreen, so you would need [GdxTestRunner] anyway.
 * - [Gdx.net] is not initalized, but shouldn't be exercised in tests anyway.
 *
 * #### Implementation notes
 * - Even though intermediates between [BaseTestRunner] and [GdxTestRunner] with degrees of mock coverage are relatively easy, we've refrained for simplicity.
 * - Note: When migrating to JUnit5, overhaul the entire architecture - our annotations become JUnit5 extensions (the Gdx specialization too),
 *   and parameterization moves off the @Parameters-static-method pattern onto @ParameterizedTest/ArgumentsProvider, which is a separate mechanism from extensions.
 */
open class BaseTestRunner(
    klass: Class<*>?,
    private val testConfig: TestWithParameters?
) : BlockJUnit4ClassRunner(klass) {
    constructor(klass: Class<*>) : this(klass, null)

    private val redirectFromClass = klass?.getAnnotation(RedirectOutput::class.java)?.policy
    private val measureDurationFromClass = klass?.getAnnotation(MeasureDuration::class.java) != null
    private val measureMemoryFromClass = klass?.getAnnotation(MeasureMemory::class.java) != null
    private val classPrefix = klass?.simpleName?.plus(".") ?: ""

    init {
        if (Gdx.files == null)
            Gdx.files = HeadlessFiles()
    }

    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        runChildWithFeatures(method, notifier)
    }

    /**
     * Applies the RedirectOutput/MeasureDuration/MeasureMemory behaviour around the actual
     * test execution ([executeChild]). Protected so [GdxTestRunner] can invoke it from the
     * render thread instead of duplicating this logic.
     */
    protected fun runChildWithFeatures(method: FrameworkMethod, notifier: RunNotifier) {
        val redirect = method.getAnnotation(RedirectOutput::class.java)?.policy ?: redirectFromClass ?: RedirectPolicy.ShowOnFailure
        val measureTime = measureDurationFromClass || method.getAnnotation(MeasureDuration::class.java) != null
        val measureMem = measureMemoryFromClass || method.getAnnotation(MeasureMemory::class.java) != null

        val startTime = if (measureTime) System.currentTimeMillis() else 0L
        val startMem = if (measureMem) MemorySnaphots.snapshot() else 0L
        when (redirect) {
            RedirectPolicy.ShowOnFailure -> runChildRedirectingOutput(method, notifier)
            RedirectPolicy.Discard -> runChildDiscardingOutput(method, notifier)
            RedirectPolicy.Show -> executeChild(method, notifier)
        }

        if (measureTime)
            println("Test $classPrefix${method.name} took ${System.currentTimeMillis() - startTime}ms.")

        if (measureMem) {
            val deltaBytes = MemorySnaphots.snapshot() - startMem
            println("Test $classPrefix${method.name} allocated ~${deltaBytes.toMB()}.")
        }
    }

    /** The actual JUnit invocation of one test method. Overridable so [GdxTestRunner] can defer it. */
    protected open fun executeChild(method: FrameworkMethod, notifier: RunNotifier) {
        super.runChild(method, notifier)
    }

    private fun runChildRedirectingOutput(method: FrameworkMethod, notifier: RunNotifier) {
        val outputBuffer = ByteArrayOutputStream(2048)
        val outputStream = PrintStream(outputBuffer)
        val oldOutputStream = System.out
        val listener = object : RunListener() {
            override fun testFailure(failure: Failure?) {
                outputBuffer.writeTo(oldOutputStream)
                super.testFailure(failure)
            }
        }

        System.setOut(outputStream)
        notifier.addListener(listener)
        try {
            executeChild(method, notifier)
        } finally {
            outputStream.close()
            System.setOut(oldOutputStream)
            notifier.removeListener(listener)
        }
    }

    private fun runChildDiscardingOutput(method: FrameworkMethod, notifier: RunNotifier) {
        val oldOutputStream = System.out
        System.setOut(PrintStream(object : OutputStream() {
            override fun write(codepoint: Int) {}
        }))
        try {
            executeChild(method, notifier)
        } finally {
            System.setOut(oldOutputStream)
        }
    }

    //region Support for running parameterized from TestRunnerFactory

    override fun validateConstructor(errors: MutableList<Throwable?>) {
        validateOnlyOneConstructor(errors)
        // Removing the validateZeroArgConstructor restriction since we allow parameterized tests.
    }

    @Throws(Exception::class)
    override fun createTest(): Any? {
        if (testConfig == null)
            return super.createTest()
        return BlockJUnit4ClassRunnerWithParameters(testConfig).createTest()
    }

    /**
     * Returns a new fixture to run a particular test `method` against.
     * Default implementation executes the no-argument [createTest] method.
     *
     * @since 4.13
     */
    @Throws(Exception::class)
    override fun createTest(method: FrameworkMethod?): Any? {
        return createTest()
    }

    override fun getName(): String =
        super.getName() + if (testConfig == null) "" else testConfig.name

    override fun testName(method: FrameworkMethod): String {
        return super.testName(method) + if (testConfig == null) "" else testConfig.parameters.toString()
    }

    //endregion
}
