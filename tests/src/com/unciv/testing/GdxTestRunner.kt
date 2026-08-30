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
import com.badlogic.gdx.graphics.GL20
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.parameterized.TestWithParameters
import org.mockito.Mockito


/**
 * Extends [BaseTestRunner] with a mock Gdx headless application loop: a mocked GL20 and
 * a render-thread on which tests actually execute. Only needed for tests that call game code
 * touching GL or Gdx-thread dispatchers — see [TestRunnerFactory.WithGdx].
 * Everything else should use [BaseTestRunner] directly, since spinning up [HeadlessApplication] isn't free.
 */
class GdxTestRunner(
    klass: Class<*>?,
    testConfig: TestWithParameters?
) : BaseTestRunner(klass, testConfig), ApplicationListener {
    constructor(klass: Class<*>) : this(klass, null)

    private val invokeInRender: MutableMap<FrameworkMethod, RunNotifier> = HashMap()
    private var myApp: MyHeadlessApplication

    init {
        Gdx.gl = Mockito.mock(GL20::class.java)
        Gdx.gl20 = Gdx.gl
        myApp = MyHeadlessApplication(this)
    }

    private class MyHeadlessApplication(listener: ApplicationListener) : HeadlessApplication(listener) {
        fun getThread(): Thread = mainLoopThread
        fun stop() { running = false }
    }

    override fun run(notifier: RunNotifier?) {
        super.run(notifier)
        // We get here after an entire test class finishes and after any `@After` methods.
        myApp.stop()  // Otherwise, we get a Thread leaked
        val mainLoopThread = myApp.getThread()
        mainLoopThread.join(1000)
        if (mainLoopThread.isAlive) {
            println("GdxTestRunner.HeadlessApplication.mainLoopThread failed to terminate")
            mainLoopThread.interrupt()
        }
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
                // Feature handling (redirect/measure) lives in the base class; this just
                // runs it on the render thread instead of the calling thread.
                runChildWithFeatures(method, notifier)
            }
            invokeInRender.clear()
        }
    }

    // Queue the test for the render thread instead of running it here directly.
    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        synchronized(invokeInRender) {
            invokeInRender[method] = notifier
        }
        waitUntilInvokedInRenderMethod(method, notifier)
    }

    private fun waitUntilInvokedInRenderMethod(method: FrameworkMethod, notifier: RunNotifier) {
        try {
            while (true) {
                Thread.sleep(10)
                if (synchronized(invokeInRender) { invokeInRender.isEmpty() })
                    break
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            synchronized(invokeInRender) { invokeInRender.remove(method) }
            notifier.fireTestFailure(Failure(describeChild(method), e))
            return
        }
    }
}
