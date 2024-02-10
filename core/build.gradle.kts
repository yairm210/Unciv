
plugins {
    id("kotlin")
}

sourceSets {
    main {
        java.srcDir("src/")
    }
}

kotlin {
    jvmToolchain(17)

    target {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
    }
}
java {
    targetCompatibility = JavaVersion.VERSION_1_8
}
