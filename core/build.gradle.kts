
plugins {
    id("kotlin")
}

sourceSets {
    main {
        java.srcDir("src/")
    }
}

kotlin {
    target {
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
    }
}
java {
    targetCompatibility = JavaVersion.VERSION_1_8
}
