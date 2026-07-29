plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gdx.tools) {
        exclude("com.badlogicgames.gdx", "gdx-backend-lwjgl")
    }
}
