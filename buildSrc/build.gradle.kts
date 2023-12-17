plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.badlogicgames.gdx:gdx-tools:1.12.1") {
        exclude("com.badlogicgames.gdx", "gdx-backend-lwjgl")
    }
}
