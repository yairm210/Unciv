import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-cio:3.5.2")
    implementation("io.ktor:ktor-client-websockets:3.5.2")
}

kotlin { jvmToolchain(21) }

sourceSets.main {
    java.srcDir("src/main/kotlin")
}

tasks.named<KotlinCompile>("compileKotlin") {
    source("../../../core/src/com/unciv/logic/multiplayer/apiv2/RawWebSocketSession.kt")
}

application { mainClass = "com.unciv.logic.multiplayer.apiv2.MainKt" }
