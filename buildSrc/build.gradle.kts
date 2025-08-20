import org.jetbrains.kotlin.konan.properties.loadProperties

val gdxVersion: String by loadProperties("${projectDir.parent}/gradle.properties")

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.badlogicgames.gdx:gdx-tools:$gdxVersion") {
        exclude("com.badlogicgames.gdx", "gdx-backend-lwjgl")
    }
}
