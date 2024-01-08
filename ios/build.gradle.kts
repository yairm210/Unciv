import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.unciv.build.BuildConfig

sourceSets {
    main {
        java.srcDir("src/")
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    compileTestJava {
        options.encoding = "UTF-8"
    }
}

extra.set("mainClassName", "IOSLauncher")

tasks {
    launchIPhoneSimulator.dependsOn(build)
    launchIPadSimulator.dependsOn(build)
    launchIOSDevice.dependsOn(build)
    createIPA.dependsOn(build)
}

robovm {
	archs = "thumbv7:arm64"
}

eclipse.project {
    name = "${BuildConfig.appName}-ios"
    natures("org.robovm.eclipse.RoboVMNature")
}
