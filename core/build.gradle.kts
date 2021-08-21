import com.unciv.build.BuildConfig

plugins {
    id("kotlin")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_7
}


sourceSets {
    main {
        java.srcDir("src/")
    }
}

eclipse.project {
    name = "${BuildConfig.appName}-core"
}
