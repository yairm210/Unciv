import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("kotlin")
}

sourceSets {
    main {
        java.srcDir("src/")
    }
}


kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        // Fail compilation on JDK APIs missing in Java 8
        freeCompilerArgs.add("-Xjdk-release=8")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}
