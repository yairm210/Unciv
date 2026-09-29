import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+ContextParameters"))
}
