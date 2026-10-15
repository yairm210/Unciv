import com.unciv.build.BuildConfig
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

// Java 21+ deprecates dynamic agent loading: https://openjdk.org/jeps/451
val mockitoAgent = configurations.create("mockitoAgent")

// Support Gradle 9 caching seeing the mockito agent
private class MockitoAgentArgumentProvider(
    @get:Classpath val agentJar: FileCollection
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-javaagent:${agentJar.singleFile}")
}

dependencies {
    testImplementation(libs.junit)
    @Suppress("AvoidDuplicateDependencies") // false positive
    testImplementation(libs.mockito)
    @Suppress("AvoidDuplicateDependencies")
    mockitoAgent(libs.mockito) { isTransitive = false }
}

tasks {
    test {
        workingDir = file("../android/assets")
        testLogging.lifecycle {
            events(
                    TestLogEvent.FAILED,
                    TestLogEvent.STANDARD_ERROR,
                    TestLogEvent.STANDARD_OUT
            )

            exceptionFormat = TestExceptionFormat.FULL
        }

        jvmArgumentProviders.add(MockitoAgentArgumentProvider(mockitoAgent))

        // Forward latency-test save file path to the test JVM
        System.getProperty("unciv.nextTurnSaveFile")?.let { systemProperty("unciv.nextTurnSaveFile", it) }
    }

    // Turn speed for the Objective Judge Horizon benchmark. A developer tool,
    // never part of `check`: it plays hundreds of turns and prints a machine
    // protocol, which is not a test and would not belong in one.
    //
    //   ./gradlew :tests:ojhTurns -Pojh.args="--turns 250 --players 63 --map /path/map.json"
    register<JavaExec>("ojh") {
        group = "verification"
        description = "Measure turn speed or frame rate for OJH (see com.unciv.dev.OjhBenchmark)"
        mainClass.set("com.unciv.dev.OjhBenchmark")
        classpath = sourceSets["test"].runtimeClasspath
        workingDir = file("../android/assets")
        args = ((project.findProperty("ojh.args") as String?) ?: "").split(" ").filter { it.isNotEmpty() }
        // The fps mode opens a real window, and GLFW on macOS insists on being
        // on the first thread of the process; headless AWT keeps AWT from
        // taking that same thread over (see OjhBenchmark.fps). Both are
        // harmless for the turns mode, which opens no window at all.
        if (System.getProperty("os.name").startsWith("Mac"))
            jvmArgs("-XstartOnFirstThread", "-Djava.awt.headless=true")
    }
}

sourceSets {
    test {
        java.srcDir("src")
    }
}

eclipse.project {
    name = "${BuildConfig.appName}-tests"
}

// Print the tests runtime classpath, so the OJH benchmark can be launched as a
// plain `java` process. Its fps mode opens a window and times frames, and a
// window owned by a Gradle worker does not get one.
tasks.register("ojhClasspath") {
    val cp = sourceSets["test"].runtimeClasspath
    doLast { println(cp.asPath) }
}
