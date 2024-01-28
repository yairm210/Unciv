
plugins {
    id("kotlin")
}

sourceSets {
    main {
        java.srcDir("src/")
    }
}

kotlin {
    jvmToolchain(11)
}
java {
    targetCompatibility = JavaVersion.VERSION_11
}
