
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
            kotlinOptions.jvmTarget = JavaVersion.VERSION_11.majorVersion
        }
    }
}
java {
    targetCompatibility = JavaVersion.VERSION_11
}
