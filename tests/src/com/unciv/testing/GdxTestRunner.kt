/*******************************************************************************
 * Copyright 2015 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.unciv.testing

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream


class GdxTestRunner(
    klass: Class<*>?,
    private val testConfig: TestWithParameters?
) : BlockJUnit4ClassRunner(klass), ApplicationListener {
    constructor(klass: Class<*>) : this(klass, null) 

    private val invokeInRender: MutableMap<FrameworkMethod, RunNotifier> = HashMap()
    private val measureDuration = klass?.getAnnotation(MeasureDuration::class.java) != null
    private val classPrefix = klass?.simpleName?.plus(".") ?: ""

    init {
        val conf = HeadlessApplicationConfiguration()
        HeadlessApplication(this, conf)
        Gdx.gl = Mockito.mock(GL20::class.java)
    }

    // ApplicationListener interface
    override fun create() {}
    override fun resume() {}
    override fun resize(width: Int, height: Int) {}
    override fun pause() {}
    override fun dispose() {}

    override fun render() {
        synchronized(invokeInRender) {
            for ((method, notifier) in invokeInRender) {
                val measure = measureDuration || method.getAnnotation(MeasureDuration::class.java) != null
                val startTime = System.currentTimeMillis()
                val redirect = method.getAnnotation(RedirectOutput::class.java)
                    ?.policy
                    ?: RedirectPolicy.ShowOnFailure
                when (redirect) {
                    RedirectPolicy.ShowOnFailure ->
                        runChildRedirectingOutput(method, notifier)
                    RedirectPolicy.Discard ->
                        runChildDiscardingOutput(method, notifier)
                    else ->
                        super.runChild(method, notifier)
                }
                if (!measure) continue
                println("Test $classPrefix${method.name} took ${System.currentTimeMillis() - startTime}ms.")
            }
            invokeInRender.clear()
        }
    }

    // BlockJUnit4ClassRunner interface
    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        synchronized(invokeInRender) {
            // add for invoking in render phase, where gl context is available
            invokeInRender.put(method, notifier)
        }
        // wait until that test was invoked
        waitUntilInvokedInRenderMethod()
    }

    private fun waitUntilInvokedInRenderMethod() {
        try {
            while (true) {
                Thread.sleep(10)
                if (synchronized(invokeInRender) { invokeInRender.isEmpty() })
                    break
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    // Standard output redirection
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
            super.runChild(method, notifier)
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
            super.runChild(method, notifier)
        } finally {
            System.setOut(oldOutputStream)
        }
    }

    protected override fun validateConstructor(errors: MutableList<Throwable?>) {
        validateOnlyOneConstructor(errors)
        //Removing the validateZeroArgConstructor restriction since we allow parameterized tests.
    }

    @Throws(Exception::class)
    protected override fun createTest(): Any? {
        if (testConfig == null)
            return super.createTest()
        return BlockJUnit4ClassRunnerWithParameters(testConfig).createTest()
    }

    /**
     * Returns a new fixture to run a particular test `method` against.
     * Default implementation executes the no-argument [.createTest] method.
     *
     * @since 4.13
     */
    @Throws(Exception::class)
    protected override fun createTest(method: FrameworkMethod?): Any? {
        return createTest()
    }

    protected override fun testName(method: FrameworkMethod): String {
        return super.testName(method) + if (testConfig == null) "" else testConfig.parameters.toString()
    }
    
}

class GdxTestRunnerFactory: ParametersRunnerFactory {
    override fun createRunnerForTestWithParameters(testConfig: TestWithParameters?): GdxTestRunner {
        return GdxTestRunner(testConfig?.testClass?.javaClass, testConfig)
    }
}
