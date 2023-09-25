
plugins {
    id("kotlin")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}


sourceSets {
    main {
        java.srcDir("src/")
    }
}
