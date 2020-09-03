import com.unciv.build.BuildConfig
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_6
}

tasks {
    test {
        workingDir = file("../android/assets")
        testLogging.debug {
            events(
                    TestLogEvent.STARTED,
                    TestLogEvent.FAILED,
                    TestLogEvent.PASSED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.STANDARD_ERROR,
                    TestLogEvent.STANDARD_OUT
            )

            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

    }

    compileJava {
        options.encoding = "UTF-8"
    }
    compileTestJava {
        options.encoding = "UTF-8"
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
