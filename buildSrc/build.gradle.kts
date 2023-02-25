plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.badlogicgames.gdx:gdx-tools:1.11.0") {
        exclude("com.badlogicgames.gdx", "gdx-backend-lwjgl")
    }
}
