import com.unciv.build.BuildConfig
import org.gradle.api.tasks.testing.logging.TestLogEvent

// Java 21+ deprecates dynamic agent loading: https://openjdk.org/jeps/451
val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.mockito)
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

            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        jvmArgs = listOf("-javaagent:${mockitoAgent.asPath}") // this actually merges into pre-existing jvm args
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
