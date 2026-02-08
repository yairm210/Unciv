package com.unciv.app.web

import com.unciv.UncivGame
import com.unciv.utils.Concurrency
import com.unciv.utils.Log

object WebJsTestRunner {
    private const val maxFailureDetails = 200
    private var started = false

    data class ClassResult(
        val className: String,
        val runCount: Int,
        val failureCount: Int,
        val ignoreCount: Int,
        val runtimeMs: Long,
        val failures: List<String>,
    )

    fun maybeStart(): Boolean {
        if (started) return false
        if (!WebJsTestInterop.isEnabled()) return false
        started = true
        WebJsTestInterop.publishState("starting")
        Concurrency.runOnGLThread("WebJsTestRunner") {
            runCatching { runSuite() }
                .onFailure { throwable ->
                    val message = "${throwable::class.simpleName}: ${throwable.message ?: "unknown error"}"
                    Log.error("Browser JS test run failed", throwable)
                    WebJsTestInterop.publishError(message)
                }
        }
        return true
    }

    private fun runSuite() {
        WebJsTestInterop.publishState("running")
        val classResults = ArrayList<ClassResult>(WebJsTestSuite.classes.size)
        var totalRun = 0
        var totalFailures = 0
        var totalIgnored = 0
        var totalRuntimeMs = 0L
        val failureDetails = ArrayList<String>()

        for (testClass in WebJsTestSuite.classes) {
            WebJsTestInterop.publishState("running:${testClass.className}")
            val result = runClass(testClass)
            classResults += result
            totalRun += result.runCount
            totalFailures += result.failureCount
            totalIgnored += result.ignoreCount
            totalRuntimeMs += result.runtimeMs
            for (failure in result.failures) {
                if (failureDetails.size >= maxFailureDetails) break
                failureDetails += "${testClass.className}: $failure"
            }
        }

        val passed = totalFailures == 0
        val json = buildJsonResult(
            passed = passed,
            classes = classResults,
            totalRun = totalRun,
            totalFailures = totalFailures,
            totalIgnored = totalIgnored,
            totalRuntimeMs = totalRuntimeMs,
            failureDetails = failureDetails,
            gameVersion = UncivGame.VERSION.text,
        )
        WebJsTestInterop.publishResult(json)
    }

    private fun runClass(testClass: WebJsGeneratedTestClass): ClassResult {
        val classStart = System.currentTimeMillis()
        var runCount = 0
        var failureCount = 0
        var ignoreCount = 0
        val failures = ArrayList<String>()

        for (testMethod in testClass.testMethods) {
            if (testMethod.ignoredReason != null) {
                ignoreCount++
                continue
            }

            runCount++
            val classInstance = runCatching { testClass.createInstance() }.getOrElse { throwable ->
                failureCount++
                failures += "${testMethod.name}: failed to create test class instance (${formatThrowable(throwable)})"
                continue
            }

            var testFailed = false
            try {
                for (before in testClass.beforeMethods) before(classInstance)
                testMethod.execute(classInstance)
            } catch (throwable: Throwable) {
                failureCount++
                testFailed = true
                failures += "${testMethod.name}: ${formatThrowable(throwable)}"
            } finally {
                for (after in testClass.afterMethods) {
                    runCatching { after(classInstance) }.onFailure { throwable ->
                        if (!testFailed) {
                            failureCount++
                            failures += "${testMethod.name}: @After failed (${formatThrowable(throwable)})"
                        }
                    }
                }
            }
        }

        return ClassResult(
            className = testClass.className,
            runCount = runCount,
            failureCount = failureCount,
            ignoreCount = ignoreCount,
            runtimeMs = System.currentTimeMillis() - classStart,
            failures = failures,
        )
    }

    private fun formatThrowable(throwable: Throwable): String {
        val message = throwable.message?.trim().orEmpty()
        val type = throwable::class.simpleName ?: throwable::class.java.simpleName
        val base = if (message.isEmpty()) type else "$type: $message"
        val stack = throwable.stackTrace
            .asSequence()
            .take(8)
            .joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        return if (stack.isBlank()) base else "$base | $stack"
    }

    private fun buildJsonResult(
        passed: Boolean,
        classes: List<ClassResult>,
        totalRun: Int,
        totalFailures: Int,
        totalIgnored: Int,
        totalRuntimeMs: Long,
        failureDetails: List<String>,
        gameVersion: String,
    ): String {
        return buildString {
            append("{")
            append("\"passed\":").append(if (passed) "true" else "false").append(",")
            append("\"summary\":{")
            append("\"classCount\":").append(classes.size).append(",")
            append("\"totalRun\":").append(totalRun).append(",")
            append("\"totalFailures\":").append(totalFailures).append(",")
            append("\"totalIgnored\":").append(totalIgnored).append(",")
            append("\"totalRuntimeMs\":").append(totalRuntimeMs).append(",")
            append("\"gameVersion\":\"").append(escapeJson(gameVersion)).append("\"")
            append("},")
            append("\"classes\":[")
            classes.forEachIndexed { index, classResult ->
                if (index > 0) append(",")
                append("{")
                append("\"className\":\"").append(escapeJson(classResult.className)).append("\",")
                append("\"runCount\":").append(classResult.runCount).append(",")
                append("\"failureCount\":").append(classResult.failureCount).append(",")
                append("\"ignoreCount\":").append(classResult.ignoreCount).append(",")
                append("\"runtimeMs\":").append(classResult.runtimeMs).append(",")
                append("\"failures\":[")
                classResult.failures.forEachIndexed { failureIndex, failure ->
                    if (failureIndex > 0) append(",")
                    append("\"").append(escapeJson(failure)).append("\"")
                }
                append("]")
                append("}")
            }
            append("],")
            append("\"failureDetails\":[")
            failureDetails.forEachIndexed { index, failure ->
                if (index > 0) append(",")
                append("\"").append(escapeJson(failure)).append("\"")
            }
            append("]")
            append("}")
        }
    }

    private fun escapeJson(raw: String): String {
        return buildString(raw.length + 8) {
            raw.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }
}
