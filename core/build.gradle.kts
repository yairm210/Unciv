import com.unciv.build.BuildConfig

plugins {
    id("kotlin")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_6
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    compileTestJava {
        options.encoding = "UTF-8"
    }
}

sourceSets {
    main {
        java.srcDir("src/")
    }
}

eclipse.project {
    name = "${BuildConfig.appName}-core"
}
