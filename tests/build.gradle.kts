import com.unciv.build.BuildConfig

plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_6
}

tasks {
    test {
        workingDir = file("../android/assets")
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
